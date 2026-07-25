package com.mrboard.system.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.mapper.GitSourceMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncScheduleService {

    private final Scheduler scheduler;
    private final GitSourceMapper gitSourceMapper;

    @PostConstruct
    public void init() {
        try {
            List<GitSource> sources = gitSourceMapper.selectList(
                    new LambdaQueryWrapper<GitSource>().eq(GitSource::getIsActive, 1)
            );
            for (GitSource source : sources) {
                schedule(source);
            }
            log.info("Initialized {} scheduled sync jobs", sources.size());
        } catch (Exception e) {
            log.error("Failed to initialize sync schedules", e);
        }
    }

    public void schedule(GitSource source) {
        if (source == null || source.getId() == null) {
            return;
        }
        String cron = source.getSyncCron();
        if (cron == null || cron.isBlank()) {
            cron = "0 */5 * * * ?";
        }
        if (!CronExpression.isValidExpression(cron)) {
            log.warn("Invalid cron expression for git source {}: {}", source.getId(), cron);
            return;
        }
        try {
            JobKey jobKey = jobKeyOf(source.getId());
            TriggerKey triggerKey = triggerKeyOf(source.getId());

            // 删除旧的
            scheduler.unscheduleJob(triggerKey);
            scheduler.deleteJob(jobKey);

            JobDataMap jobData = new JobDataMap();
            jobData.put("gitSourceId", source.getId());

            JobDetail jobDetail = JobBuilder.newJob(SyncJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(jobData)
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron).withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled sync job for git source {} with cron: {}", source.getId(), cron);
        } catch (SchedulerException e) {
            log.error("Failed to schedule sync job for git source {}", source.getId(), e);
        }
    }

    public void reschedule(Long gitSourceId) {
        GitSource source = gitSourceMapper.selectById(gitSourceId);
        if (source == null || source.getIsActive() == null || source.getIsActive() != 1) {
            remove(gitSourceId);
            return;
        }
        schedule(source);
    }

    public void remove(Long gitSourceId) {
        try {
            JobKey jobKey = jobKeyOf(gitSourceId);
            TriggerKey triggerKey = triggerKeyOf(gitSourceId);
            scheduler.unscheduleJob(triggerKey);
            scheduler.deleteJob(jobKey);
            log.info("Removed sync job for git source {}", gitSourceId);
        } catch (SchedulerException e) {
            log.error("Failed to remove sync job for git source {}", gitSourceId, e);
        }
    }

    private JobKey jobKeyOf(Long gitSourceId) {
        return new JobKey("syncJob_" + gitSourceId, "sync");
    }

    private TriggerKey triggerKeyOf(Long gitSourceId) {
        return new TriggerKey("syncTrigger_" + gitSourceId, "sync");
    }
}
