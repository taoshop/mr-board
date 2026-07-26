package com.mrboard.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.MrComment;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.SyncLog;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrCommentMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.SyncLogMapper;
import com.mrboard.system.sync.GitClientFactory;
import com.mrboard.system.sync.GitSyncClient;
import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.CommentDTO;
import com.mrboard.system.sync.dto.MrDTO;
import com.mrboard.system.websocket.SyncStatusMessage;
import com.mrboard.system.websocket.SyncWebSocketHandler;
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
    private final MrCommentMapper commentMapper;
    private final SyncLogMapper syncLogMapper;
    private final BoardStatusCalculator boardStatusCalculator;
    private final SyncWebSocketHandler syncWebSocketHandler;

    @Transactional(rollbackFor = Exception.class)
    public void triggerSync(Long gitSourceId, boolean full, String triggerType) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getGitSourceId, gitSourceId).eq(Project::getIsActive, 1);
        List<Project> projects = projectMapper.selectList(wrapper);
        for (Project project : projects) {
            try {
                if (full) {
                    project.setLastSyncAt(null);
                    projectMapper.updateById(project);
                }
                syncProject(project.getId(), triggerType);
            } catch (Exception e) {
                log.error("Trigger sync failed for project {}: {}", project.getId(), e.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncLog syncProject(Long projectId, String triggerType) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        boolean isFull = project.getLastSyncAt() == null;
        return isFull
                ? fullSyncProject(project, triggerType)
                : incrementalSyncProject(project, triggerType);
    }

    /**
     * 全量同步：拉取所有 MR（忽略 lastSyncAt），适用于首次同步或手动触发
     */
    @Transactional(rollbackFor = Exception.class)
    public SyncLog fullSyncProject(Project project, String triggerType) {
        project.setLastSyncAt(null);
        projectMapper.updateById(project);
        return doSync(project, triggerType, "full");
    }

    /**
     * 增量同步：仅拉取 lastSyncAt 之后更新的 MR
     */
    @Transactional(rollbackFor = Exception.class)
    public SyncLog incrementalSyncProject(Project project, String triggerType) {
        return doSync(project, triggerType, "incremental");
    }

    /**
     * 核心同步逻辑（全量/增量共用）
     */
    private SyncLog doSync(Project project, String triggerType, String syncType) {
        Long projectId = project.getId();
        SyncLog logRecord = new SyncLog();
        logRecord.setProjectId(projectId);
        logRecord.setGitSourceId(project.getGitSourceId());
        logRecord.setSyncType(syncType);
        logRecord.setTriggerType(triggerType);
        logRecord.setStatus("running");
        logRecord.setCreatedAt(LocalDateTime.now());
        syncLogMapper.insert(logRecord);

        // WebSocket 推送：同步开始
        syncWebSocketHandler.broadcastSyncStatus(SyncStatusMessage.builder()
                .type("sync_started")
                .projectId(projectId)
                .gitSourceId(project.getGitSourceId())
                .projectName(project.getName())
                .status("running")
                .timestamp(LocalDateTime.now().toString())
                .build());

        int mrCount = 0;
        int ciCount = 0;
        String errorMsg = null;

        try {
            GitSyncClient client = gitClientFactory.create(project.getGitSourceId());

            LocalDateTime lastSync = project.getLastSyncAt();
            String updatedAfter = lastSync != null
                    ? lastSync.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
                    : null;

            // state="all" 确保同时获取 open / closed / merged 状态的 MR
            List<MrDTO> mrList = client.fetchMRs(project.getProjectPath(), "all", updatedAfter);
            for (MrDTO dto : mrList) {
                Mrs savedMr = saveOrUpdateMr(projectId, dto);
                mrCount++;

                // 拉取并保存 reviewer / approval 状态
                try {
                    List<String> reviewers = client.fetchReviewers(project.getProjectPath(), dto.getPlatformMrId());
                    String approvalStatus = client.fetchApprovalStatus(project.getProjectPath(), dto.getPlatformMrId());

                    // 如果 fetchReviewers 返回空但 MR DTO 中有 reviewers，使用 DTO 中的
                    if ((reviewers == null || reviewers.isEmpty()) && dto.getReviewers() != null && !dto.getReviewers().isEmpty()) {
                        reviewers = dto.getReviewers();
                    }

                    savedMr.setReviewers(reviewers != null && !reviewers.isEmpty()
                            ? String.join(",", reviewers)
                            : null);
                    savedMr.setApprovalStatus(approvalStatus);

                    // 重新计算 boardStatus（因为 approvalStatus 已更新）
                    String ciStatus = savedMr.getCiStatus();
                    if (ciStatus == null) ciStatus = "unknown";
                    String newBoardStatus = boardStatusCalculator.calculate(
                            savedMr.getPlatformStatus(),
                            savedMr.getHasConflict(),
                            ciStatus,
                            savedMr.getMergeable(),
                            savedMr.getTitle(),
                            approvalStatus,
                            reviewers
                    );
                    savedMr.setBoardStatus(newBoardStatus);
                    mrsMapper.updateById(savedMr);
                } catch (Exception e) {
                    log.warn("Failed to fetch review data for MR {} in project {}: {}",
                            dto.getPlatformMrId(), projectId, e.getMessage());
                }

                List<CiDTO> ciList = client.fetchCI(project.getProjectPath(), dto.getPlatformMrId());
                for (CiDTO ci : ciList) {
                    saveOrUpdateCi(projectId, dto.getPlatformMrId(), ci);
                    ciCount++;
                }

                // Fetch and cache MR comments
                try {
                    List<CommentDTO> comments = client.fetchComments(project.getProjectPath(), dto.getPlatformMrId());
                    for (CommentDTO c : comments) {
                        saveOrUpdateComment(savedMr.getId(), c);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch comments for MR {} in project {}: {}", dto.getPlatformMrId(), projectId, e.getMessage());
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

        // WebSocket 推送：同步完成/失败
        String finalType = "success".equals(logRecord.getStatus()) ? "sync_completed" : "sync_failed";
        syncWebSocketHandler.broadcastSyncStatus(SyncStatusMessage.builder()
                .type(finalType)
                .projectId(projectId)
                .gitSourceId(project.getGitSourceId())
                .projectName(project.getName())
                .status(logRecord.getStatus())
                .mrCount(mrCount)
                .ciCount(ciCount)
                .errorMsg(errorMsg)
                .timestamp(LocalDateTime.now().toString())
                .build());

        return logRecord;
    }

    private Mrs saveOrUpdateMr(Long projectId, MrDTO dto) {
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

        // 初始计算 boardStatus（不含 review 数据，后续会重新计算）
        String boardStatus = boardStatusCalculator.calculate(
                dto.getPlatformStatus(), dto.getHasConflict(), ciStatus,
                dto.getMergeable(), dto.getTitle(), "pending", dto.getReviewers()
        );
        entity.setBoardStatus(boardStatus);

        if (existing != null) {
            entity.setId(existing.getId());
            mrsMapper.updateById(entity);
            return entity;
        } else {
            mrsMapper.insert(entity);
            return entity;
        }
    }

    private void saveOrUpdateComment(Long mrId, CommentDTO dto) {
        LambdaQueryWrapper<MrComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MrComment::getMrId, mrId)
                .eq(MrComment::getPlatformCommentId, dto.getPlatformCommentId());
        MrComment existing = commentMapper.selectOne(wrapper);

        MrComment entity = new MrComment();
        entity.setMrId(mrId);
        entity.setPlatformCommentId(dto.getPlatformCommentId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setAuthorAvatar(dto.getAuthorAvatar());
        entity.setBody(dto.getBody());
        entity.setIsSystem(Boolean.TRUE.equals(dto.getIsSystem()) ? 1 : 0);
        entity.setCreatedAt(dto.getCreatedAt());

        if (existing != null) {
            entity.setId(existing.getId());
            commentMapper.updateById(entity);
        } else {
            commentMapper.insert(entity);
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
