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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class SyncService {

    private final GitClientFactory gitClientFactory;
    private final ProjectMapper projectMapper;
    private final MrsMapper mrsMapper;
    private final CiJobMapper ciJobMapper;
    private final MrCommentMapper commentMapper;
    private final SyncLogMapper syncLogMapper;
    private final BoardStatusCalculator boardStatusCalculator;
    private final SyncWebSocketHandler syncWebSocketHandler;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<Long, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    public SyncService(GitClientFactory gitClientFactory, ProjectMapper projectMapper,
                        MrsMapper mrsMapper, CiJobMapper ciJobMapper,
                        MrCommentMapper commentMapper, SyncLogMapper syncLogMapper,
                        BoardStatusCalculator boardStatusCalculator,
                        SyncWebSocketHandler syncWebSocketHandler,
                        CacheManager cacheManager,
                        PlatformTransactionManager transactionManager) {
        this.gitClientFactory = gitClientFactory;
        this.projectMapper = projectMapper;
        this.mrsMapper = mrsMapper;
        this.ciJobMapper = ciJobMapper;
        this.commentMapper = commentMapper;
        this.syncLogMapper = syncLogMapper;
        this.boardStatusCalculator = boardStatusCalculator;
        this.syncWebSocketHandler = syncWebSocketHandler;
        this.cacheManager = cacheManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("syncExecutor")
    public void triggerSyncAsync(Long gitSourceId, boolean full, String triggerType) {
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
    public SyncLog fullSyncProject(Project project, String triggerType) {
        project.setLastSyncAt(null);
        projectMapper.updateById(project);
        return doSync(project, triggerType, "full");
    }

    /**
     * 增量同步：仅拉取 lastSyncAt 之后更新的 MR
     */
    public SyncLog incrementalSyncProject(Project project, String triggerType) {
        return doSync(project, triggerType, "incremental");
    }

    /**
     * 核心同步逻辑（全量/增量共用）
     *
     * <p>按 projectId 加互斥锁，确保同一项目不会并发同步，避免锁等待超时和唯一键冲突。</p>
     * <p>使用 TransactionTemplate 手动控制事务，确保每个项目的同步是独立事务，且事务范围只包含数据库操作。</p>
     */
    private SyncLog doSync(Project project, String triggerType, String syncType) {
        Long projectId = project.getId();
        ReentrantLock lock = projectLocks.computeIfAbsent(projectId, k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("同步被中断");
        }
        if (!acquired) {
            throw new RuntimeException("该项目同步任务正在执行，请稍后再试");
        }
        try {
            return transactionTemplate.execute(status -> doSyncInternal(project, triggerType, syncType));
        } finally {
            lock.unlock();
        }
    }

    private SyncLog doSyncInternal(Project project, String triggerType, String syncType) {
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
            String updatedAfter = null;
            if (lastSync != null) {
                updatedAfter = lastSync.atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toString();
            }

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
                    // 若手动拖拽设置了状态，保留手动状态，不覆盖
                    if (savedMr.getManualStatus() == null) {
                        savedMr.setBoardStatus(newBoardStatus);
                    }
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

                // 根据拉取的 CI jobs 计算整体 CI 状态并更新 MR
                String overallCiStatus = calculateOverallCiStatus(ciList);
                savedMr.setCiStatus(overallCiStatus);

                // 重新计算 boardStatus（ciStatus 已更新）
                String currentApprovalStatus = savedMr.getApprovalStatus();
                if (currentApprovalStatus == null) currentApprovalStatus = "pending";
                List<String> currentReviewers = savedMr.getReviewers() != null
                        && !savedMr.getReviewers().isEmpty()
                        ? List.of(savedMr.getReviewers().split(","))
                        : List.of();
                String newBoardStatus = boardStatusCalculator.calculate(
                        savedMr.getPlatformStatus(),
                        savedMr.getHasConflict(),
                        overallCiStatus,
                        savedMr.getMergeable(),
                        savedMr.getTitle(),
                        currentApprovalStatus,
                        currentReviewers
                );

                // 若用户手动拖拽设置了状态，同步保留手动状态（除非 MR 在平台已合并/关闭）
                if (savedMr.getManualStatus() != null) {
                    String platformStatus = savedMr.getPlatformStatus();
                    if ("merged".equalsIgnoreCase(platformStatus) || "closed".equalsIgnoreCase(platformStatus)) {
                        savedMr.setBoardStatus(newBoardStatus);
                        savedMr.setManualStatus(null);
                    }
                } else {
                    savedMr.setBoardStatus(newBoardStatus);
                }
                mrsMapper.updateById(savedMr);

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

        // 同步成功后清除看板缓存，确保前端立即看到最新数据
        if ("success".equals(logRecord.getStatus())) {
            var cache = cacheManager.getCache("board");
            if (cache != null) {
                cache.clear();
                log.debug("Cleared board cache after sync for project {}", projectId);
            }
        }

        return logRecord;
    }

    private String calculateOverallCiStatus(List<CiDTO> ciList) {
        if (ciList == null || ciList.isEmpty()) {
            return "unknown";
        }
        boolean hasRunning = ciList.stream()
                .anyMatch(ci -> "running".equalsIgnoreCase(ci.getStatus()) || "pending".equalsIgnoreCase(ci.getStatus()));
        boolean hasFailed = ciList.stream()
                .anyMatch(ci -> "failed".equalsIgnoreCase(ci.getStatus()));
        boolean hasCanceled = ciList.stream()
                .anyMatch(ci -> "cancelled".equalsIgnoreCase(ci.getStatus()) || "skipped".equalsIgnoreCase(ci.getStatus())
                        || "canceled".equalsIgnoreCase(ci.getStatus()));
        if (hasRunning) return "running";
        if (hasFailed) return "failed";
        // cancelled/skipped 不应归为 success，返回 unknown
        if (hasCanceled && ciList.stream().noneMatch(ci -> "success".equalsIgnoreCase(ci.getStatus()))) {
            return "unknown";
        }
        return "success";
    }

    private Mrs saveOrUpdateMr(Long projectId, MrDTO dto) {
        LambdaQueryWrapper<Mrs> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Mrs::getProjectId, projectId)
                .eq(Mrs::getPlatformMrId, dto.getPlatformMrId());
        List<Mrs> existingList = mrsMapper.selectList(wrapper);
        Mrs existing = null;
        if (!existingList.isEmpty()) {
            // 如果有重复记录，保留 id 最大的（最新），删除多余的
            existing = existingList.stream()
                    .max(java.util.Comparator.comparing(Mrs::getId))
                    .orElse(null);
            for (Mrs dup : existingList) {
                if (!dup.getId().equals(existing.getId())) {
                    mrsMapper.deleteById(dup.getId());
                    log.warn("Deleted duplicate MR record: id={}, projectId={}, platformMrId={}",
                            dup.getId(), projectId, dto.getPlatformMrId());
                }
            }
        }

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

        String boardStatus = boardStatusCalculator.calculate(
                dto.getPlatformStatus(), dto.getHasConflict(), ciStatus,
                dto.getMergeable(), dto.getTitle(), "pending", dto.getReviewers()
        );

        if (existing != null) {
            entity.setId(existing.getId());
            entity.setReviewers(existing.getReviewers());
            entity.setApprovalStatus(existing.getApprovalStatus());
            entity.setManualStatus(existing.getManualStatus());
            // 若手动拖拽设置了状态，保留手动状态，不覆盖
            if (existing.getManualStatus() != null) {
                entity.setBoardStatus(existing.getManualStatus());
            } else {
                entity.setBoardStatus(boardStatus);
            }
            mrsMapper.updateById(entity);
            return entity;
        } else {
            entity.setBoardStatus(boardStatus);
            try {
                mrsMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException e) {
                // 并发场景下防御性处理：唯一键冲突时回退到更新
                log.warn("Duplicate key when inserting MR (projectId={}, platformMrId={}), falling back to update",
                        projectId, dto.getPlatformMrId());
                existingList = mrsMapper.selectList(wrapper);
                if (!existingList.isEmpty()) {
                    existing = existingList.stream()
                            .max(java.util.Comparator.comparing(Mrs::getId))
                            .orElse(null);
                    entity.setId(existing.getId());
                    entity.setReviewers(existing.getReviewers());
                    entity.setApprovalStatus(existing.getApprovalStatus());
                    entity.setManualStatus(existing.getManualStatus());
                    if (existing.getManualStatus() != null) {
                        entity.setBoardStatus(existing.getManualStatus());
                    } else {
                        entity.setBoardStatus(boardStatus);
                    }
                    mrsMapper.updateById(entity);
                    return entity;
                }
                throw e;
            }
        }
    }

    private void saveOrUpdateComment(Long mrId, CommentDTO dto) {
        LambdaQueryWrapper<MrComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MrComment::getMrId, mrId)
                .eq(MrComment::getPlatformCommentId, dto.getPlatformCommentId());
        List<MrComment> existingList = commentMapper.selectList(wrapper);
        MrComment existing = null;
        if (!existingList.isEmpty()) {
            existing = existingList.stream()
                    .max(java.util.Comparator.comparing(MrComment::getId))
                    .orElse(null);
            for (MrComment dup : existingList) {
                if (!dup.getId().equals(existing.getId())) {
                    commentMapper.deleteById(dup.getId());
                    log.warn("Deleted duplicate comment record: id={}, mrId={}, platformCommentId={}",
                            dup.getId(), mrId, dto.getPlatformCommentId());
                }
            }
        }

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
        List<CiJob> existingList = ciJobMapper.selectList(wrapper);
        CiJob existing = null;
        if (!existingList.isEmpty()) {
            existing = existingList.stream()
                    .max(java.util.Comparator.comparing(CiJob::getId))
                    .orElse(null);
            for (CiJob dup : existingList) {
                if (!dup.getId().equals(existing.getId())) {
                    ciJobMapper.deleteById(dup.getId());
                    log.warn("Deleted duplicate CI job record: id={}, projectId={}, platformJobId={}",
                            dup.getId(), projectId, dto.getPlatformJobId());
                }
            }
        }

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
