package com.mrboard.system.job;

import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncJob implements Job {

    private final ProjectMapper projectMapper;
    private final SyncService syncService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Scheduled sync job started");
        List<Project> projects = projectMapper.selectList(null);
        for (Project project : projects) {
            try {
                syncService.syncProject(project.getId());
            } catch (Exception e) {
                log.error("Scheduled sync failed for project {}: {}", project.getId(), e.getMessage());
            }
        }
        log.info("Scheduled sync job finished");
    }
}
