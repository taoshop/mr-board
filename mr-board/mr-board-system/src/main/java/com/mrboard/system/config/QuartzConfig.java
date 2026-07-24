package com.mrboard.system.config;

import com.mrboard.system.job.SyncJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail syncJobDetail() {
        return JobBuilder.newJob(SyncJob.class)
                .withIdentity("syncJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger syncJobTrigger() {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(5)
                .repeatForever();
        return TriggerBuilder.newTrigger()
                .forJob(syncJobDetail())
                .withIdentity("syncTrigger")
                .withSchedule(scheduleBuilder)
                .build();
    }
}
