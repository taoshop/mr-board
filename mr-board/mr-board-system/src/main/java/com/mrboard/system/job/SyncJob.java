package com.mrboard.system.job;

import com.mrboard.system.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncJob implements Job {

    private final SyncService syncService;

    @Override
    public void execute(JobExecutionContext context) {
        Long gitSourceId = context.getMergedJobDataMap().getLong("gitSourceId");
        if (gitSourceId == null) {
            log.warn("No gitSourceId in job data, skipping");
            return;
        }
        log.info("Scheduled sync job started for git source {}", gitSourceId);
        try {
            syncService.triggerSync(gitSourceId, false, "scheduled");
        } catch (Exception e) {
            log.error("Scheduled sync failed for git source {}: {}", gitSourceId, e.getMessage());
        }
        log.info("Scheduled sync job finished for git source {}", gitSourceId);
    }
}
