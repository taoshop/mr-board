package com.mrboard.system.vo.report;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportOverviewVO {
    private Integer totalMrCount;          // 总 MR 数
    private Integer mergedMrCount;         // 已合并 MR 数
    private Integer openMrCount;           // 开放中 MR 数
    private BigDecimal avgMergeHours;      // 平均合并时长（小时）
    private BigDecimal ciSuccessRate;      // CI 成功率
    private BigDecimal conflictRate;       // 冲突率
}
