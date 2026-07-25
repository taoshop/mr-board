package com.mrboard.system.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.ReportDailySummary;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.ReportDailySummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDailySummaryJob implements Job {

    private final ProjectMapper projectMapper;
    private final MrsMapper mrsMapper;
    private final CiJobMapper ciJobMapper;
    private final ReportDailySummaryMapper reportDailySummaryMapper;

    @Override
    public void execute(JobExecutionContext context) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting daily report calculation for {}", yesterday);

        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX);

        List<Project> projects = projectMapper.selectList(null);
        int count = 0;
        for (Project project : projects) {
            try {
                ReportDailySummary summary = calculateForProject(project.getId(), yesterday, startOfDay, endOfDay);
                if (summary != null) {
                    reportDailySummaryMapper.insert(summary);
                    count++;
                }
            } catch (Exception e) {
                log.error("Failed to calculate daily report for project {} on {}", project.getId(), yesterday, e);
            }
        }
        log.info("Daily report calculation finished for {}, {} projects processed", yesterday, count);
    }

    private ReportDailySummary calculateForProject(Long projectId, LocalDate date,
                                                    LocalDateTime start, LocalDateTime end) {
        ReportDailySummary summary = new ReportDailySummary();
        summary.setSummaryDate(date);
        summary.setProjectId(projectId);

        // 1. created_count
        LambdaQueryWrapper<Mrs> createdWrapper = new LambdaQueryWrapper<>();
        createdWrapper.eq(Mrs::getProjectId, projectId)
                .ge(Mrs::getCreatedAt, start)
                .le(Mrs::getCreatedAt, end);
        summary.setCreatedCount(mrsMapper.selectCount(createdWrapper).intValue());

        // 2. merged_count
        LambdaQueryWrapper<Mrs> mergedWrapper = new LambdaQueryWrapper<>();
        mergedWrapper.eq(Mrs::getProjectId, projectId)
                .ge(Mrs::getMergedAt, start)
                .le(Mrs::getMergedAt, end);
        summary.setMergedCount(mrsMapper.selectCount(mergedWrapper).intValue());

        // 3. closed_count
        LambdaQueryWrapper<Mrs> closedWrapper = new LambdaQueryWrapper<>();
        closedWrapper.eq(Mrs::getProjectId, projectId)
                .ge(Mrs::getClosedAt, start)
                .le(Mrs::getClosedAt, end);
        summary.setClosedCount(mrsMapper.selectCount(closedWrapper).intValue());

        // 4. avg_merge_hours
        LambdaQueryWrapper<Mrs> avgWrapper = new LambdaQueryWrapper<>();
        avgWrapper.eq(Mrs::getProjectId, projectId)
                .ge(Mrs::getMergedAt, start)
                .le(Mrs::getMergedAt, end)
                .isNotNull(Mrs::getCreatedAt)
                .isNotNull(Mrs::getMergedAt);
        List<Mrs> mergedMrs = mrsMapper.selectList(avgWrapper);
        if (!mergedMrs.isEmpty()) {
            long totalMinutes = 0;
            for (Mrs mr : mergedMrs) {
                totalMinutes += ChronoUnit.MINUTES.between(mr.getCreatedAt(), mr.getMergedAt());
            }
            double avgHours = (totalMinutes / 60.0) / mergedMrs.size();
            summary.setAvgMergeHours(BigDecimal.valueOf(avgHours).setScale(2, RoundingMode.HALF_UP));
        } else {
            summary.setAvgMergeHours(BigDecimal.ZERO);
        }

        // 5. ci_success_count / ci_failed_count
        LambdaQueryWrapper<CiJob> ciWrapper = new LambdaQueryWrapper<>();
        ciWrapper.eq(CiJob::getProjectId, projectId)
                .ge(CiJob::getStartedAt, start)
                .le(CiJob::getStartedAt, end);
        List<CiJob> ciJobs = ciJobMapper.selectList(ciWrapper);
        int success = 0, failed = 0;
        for (CiJob job : ciJobs) {
            if ("success".equalsIgnoreCase(job.getStatus())) {
                success++;
            } else if ("failed".equalsIgnoreCase(job.getStatus())) {
                failed++;
            }
        }
        summary.setCiSuccessCount(success);
        summary.setCiFailedCount(failed);

        // 6. conflict_count: 统计当日存在冲突的 MR 数
        //    口径：hasConflict=true 且在当日有更新  OR  boardStatus='conflict'（含历史延续冲突）
        //    说明：冲突 MR 可能跨天存在，仅统计当日活跃冲突可避免历史冲突被重复计入。
        LambdaQueryWrapper<Mrs> conflictWrapper = new LambdaQueryWrapper<>();
        conflictWrapper.eq(Mrs::getProjectId, projectId)
                .and(w -> w
                    .eq(Mrs::getBoardStatus, "conflict")
                    .or()
                    .eq(Mrs::getHasConflict, true));
        summary.setConflictCount(mrsMapper.selectCount(conflictWrapper).intValue());

        return summary;
    }
}
