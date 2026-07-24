package com.mrboard.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.SyncLog;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.SyncLogMapper;
import com.mrboard.system.sync.GitClientFactory;
import com.mrboard.system.sync.GitSyncClient;
import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.MrDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final GitClientFactory gitClientFactory;
    private final ProjectMapper projectMapper;
    private final MrsMapper mrsMapper;
    private final CiJobMapper ciJobMapper;
    private final SyncLogMapper syncLogMapper;
    private final BoardStatusCalculator boardStatusCalculator;

    @Transactional(rollbackFor = Exception.class)
    public void triggerSync(Long gitSourceId, boolean full) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getGitSourceId, gitSourceId).eq(Project::getIsActive, 1);
        List<Project> projects = projectMapper.selectList(wrapper);
        for (Project project : projects) {
            try {
                if (full) {
                    project.setLastSyncAt(null);
                    projectMapper.updateById(project);
                }
                syncProject(project.getId());
            } catch (Exception e) {
                log.error("Trigger sync failed for project {}: {}", project.getId(), e.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncLog syncProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }

        SyncLog logRecord = new SyncLog();
        logRecord.setProjectId(projectId);
        logRecord.setGitSourceId(project.getGitSourceId());
        logRecord.setSyncType("incremental");
        logRecord.setStatus("running");
        logRecord.setCreatedAt(LocalDateTime.now());
        syncLogMapper.insert(logRecord);

        int mrCount = 0;
        int ciCount = 0;
        String errorMsg = null;

        try {
            GitSyncClient client = gitClientFactory.create(project.getGitSourceId());

            LocalDateTime lastSync = project.getLastSyncAt();
            String updatedAfter = lastSync != null
                    ? lastSync.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
                    : null;

            List<MrDTO> mrList = client.fetchMRs(project.getProjectPath(), null, updatedAfter);
            for (MrDTO dto : mrList) {
                saveOrUpdateMr(projectId, dto);
                mrCount++;

                List<CiDTO> ciList = client.fetchCI(project.getProjectPath(), dto.getPlatformMrId());
                for (CiDTO ci : ciList) {
                    saveOrUpdateCi(projectId, dto.getPlatformMrId(), ci);
                    ciCount++;
                }
            }

            LambdaUpdateWrapper<Project> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Project::getId, projectId)
                    .set(Project::getLastSyncAt, LocalDateTime.now())
                    .set(Project::getMrCount, mrsMapper.selectCount(
                            new LambdaQueryWrapper<Mrs>().eq(Mrs::getProjectId, projectId)
                    ));
            projectMapper.update(updateWrapper);

            logRecord.setStatus("success");
        } catch (Exception e) {
            log.error("Sync failed for project {}: {}", projectId, e.getMessage(), e);
            errorMsg = e.getMessage();
            logRecord.setStatus("failed");
            logRecord.setErrorMsg(errorMsg);
        }

        logRecord.setMrCount(mrCount);
        logRecord.setCiCount(ciCount);
        logRecord.setFinishedAt(LocalDateTime.now());
        syncLogMapper.updateById(logRecord);
        return logRecord;
    }

    private void saveOrUpdateMr(Long projectId, MrDTO dto) {
        LambdaQueryWrapper<Mrs> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Mrs::getProjectId, projectId)
                .eq(Mrs::getPlatformMrId, dto.getPlatformMrId());
        Mrs existing = mrsMapper.selectOne(wrapper);

        Mrs entity = new Mrs();
        entity.setProjectId(projectId);
        entity.setPlatformMrId(dto.getPlatformMrId());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setAuthorName(dto.getAuthorName());
        entity.setAuthorAvatar(dto.getAuthorAvatar());
        entity.setAssigneeName(dto.getAssigneeName());
        entity.setSourceBranch(dto.getSourceBranch());
        entity.setTargetBranch(dto.getTargetBranch());
        entity.setPlatformStatus(dto.getPlatformStatus());
        entity.setHasConflict(dto.getHasConflict());
        entity.setMergeable(dto.getMergeable());
        entity.setChangesCount(dto.getChangesCount());
        entity.setAdditions(dto.getAdditions());
        entity.setDeletions(dto.getDeletions());
        entity.setCommentsCount(dto.getCommentsCount());
        entity.setWebUrl(dto.getWebUrl());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setMergedAt(dto.getMergedAt());
        entity.setClosedAt(dto.getClosedAt());
        entity.setLastSyncAt(LocalDateTime.now());

        String ciStatus = dto.getCiStatus();
        if (ciStatus == null) {
            ciStatus = "unknown";
        }
        entity.setCiStatus(ciStatus);

        String boardStatus = boardStatusCalculator.calculate(dto.getPlatformStatus(), dto.getHasConflict(), ciStatus, dto.getMergeable(), dto.getTitle());
        entity.setBoardStatus(boardStatus);

        if (existing != null) {
            entity.setId(existing.getId());
            mrsMapper.updateById(entity);
        } else {
            mrsMapper.insert(entity);
        }
    }

    private void saveOrUpdateCi(Long projectId, Long platformMrId, CiDTO dto) {
        LambdaQueryWrapper<CiJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CiJob::getProjectId, projectId)
                .eq(CiJob::getPlatformJobId, dto.getPlatformJobId());
        CiJob existing = ciJobMapper.selectOne(wrapper);

        CiJob entity = new CiJob();
        entity.setProjectId(projectId);
        entity.setPlatformMrId(platformMrId);
        entity.setPlatformJobId(dto.getPlatformJobId());
        entity.setName(dto.getName());
        entity.setStage(dto.getStage());
        entity.setStatus(dto.getStatus());
        entity.setLogUrl(dto.getLogUrl());
        entity.setStartedAt(dto.getStartedAt());
        entity.setFinishedAt(dto.getFinishedAt());

        if (existing != null) {
            entity.setId(existing.getId());
            ciJobMapper.updateById(entity);
        } else {
            ciJobMapper.insert(entity);
        }
    }
}
