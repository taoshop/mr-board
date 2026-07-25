package com.mrboard.system.job;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleService {

    private final Scheduler scheduler;

    @PostConstruct
    public void initReportJob() {
        try {
            JobKey jobKey = new JobKey("reportDailySummaryJob", "report");
            if (scheduler.checkExists(jobKey)) {
                log.info("Report daily summary job already exists, skipping registration.");
                return;
            }

            JobDetail job = newJob(ReportDailySummaryJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();

            CronTrigger trigger = newTrigger()
                    .withIdentity("reportDailySummaryTrigger", "report")
                    .withSchedule(cronSchedule("0 0 2 * * ?"))
                    .forJob(job)
                    .build();

            scheduler.scheduleJob(job, trigger);
            log.info("Registered report daily summary job with cron: 0 0 2 * * ?");
        } catch (SchedulerException e) {
            log.error("Failed to register report daily summary job", e);
        }
    }
}
