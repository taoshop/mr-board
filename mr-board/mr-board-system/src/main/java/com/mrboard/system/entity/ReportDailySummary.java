package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("report_daily_summary")
public class ReportDailySummary {

    private LocalDate summaryDate;
    private Long projectId;
    private Integer createdCount;
    private Integer mergedCount;
    private Integer closedCount;
    private BigDecimal avgMergeHours;
    private Integer ciSuccessCount;
    private Integer ciFailedCount;
    private Integer conflictCount;
}
