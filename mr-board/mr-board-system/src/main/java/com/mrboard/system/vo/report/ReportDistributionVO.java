package com.mrboard.system.vo.report;

import lombok.Data;

import java.util.List;

@Data
public class ReportDistributionVO {
    private List<String> labels;
    private List<Integer> values;
}
