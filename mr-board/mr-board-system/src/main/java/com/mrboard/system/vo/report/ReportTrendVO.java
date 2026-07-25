package com.mrboard.system.vo.report;

import lombok.Data;

import java.util.List;

@Data
public class ReportTrendVO {
    private List<String> labels;            // 日期标签
    private List<Integer> createdData;     // 创建数
    private List<Integer> mergedData;      // 合并数
    private List<Integer> closedData;      // 关闭数
}
