package com.mrboard.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrboard.common.utils.MathUtils;
import com.mrboard.system.dto.MrExportDTO;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.ReportDailySummary;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.ReportDailySummaryMapper;
import com.mrboard.system.vo.report.ReportDistributionVO;
import com.mrboard.system.vo.report.ReportOverviewVO;
import com.mrboard.system.vo.report.ReportTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDailySummaryMapper reportDailySummaryMapper;
    private final MrsMapper mrsMapper;
    private final ProjectMapper projectMapper;

    /**
     * 概览指标：基于预计算表聚合
     */
    public ReportOverviewVO getOverview(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<ReportDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ReportDailySummary::getSummaryDate, startDate)
                .le(ReportDailySummary::getSummaryDate, endDate);
        List<ReportDailySummary> list = reportDailySummaryMapper.selectList(wrapper);

        ReportOverviewVO vo = new ReportOverviewVO();
        if (list.isEmpty()) {
            vo.setTotalMrCount(0);
            vo.setMergedMrCount(0);
            vo.setOpenMrCount(0);
            vo.setAvgMergeHours(BigDecimal.ZERO);
            vo.setCiSuccessRate(BigDecimal.ZERO);
            vo.setConflictRate(BigDecimal.ZERO);
            return vo;
        }

        int totalCreated = list.stream().mapToInt(ReportDailySummary::getCreatedCount).sum();
        int totalMerged = list.stream().mapToInt(ReportDailySummary::getMergedCount).sum();
        int totalClosed = list.stream().mapToInt(ReportDailySummary::getClosedCount).sum();
        int totalConflict = list.stream().mapToInt(ReportDailySummary::getConflictCount).sum();
        int totalCiSuccess = list.stream().mapToInt(ReportDailySummary::getCiSuccessCount).sum();
        int totalCiFailed = list.stream().mapToInt(ReportDailySummary::getCiFailedCount).sum();

        double totalMergeHoursWeighted = list.stream()
                .filter(r -> r.getAvgMergeHours() != null && r.getMergedCount() != null && r.getMergedCount() > 0)
                .mapToDouble(r -> r.getAvgMergeHours().doubleValue() * r.getMergedCount())
                .sum();
        BigDecimal avgMergeHours = totalMerged > 0
                ? BigDecimal.valueOf(totalMergeHoursWeighted / totalMerged).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int totalCi = totalCiSuccess + totalCiFailed;

        vo.setTotalMrCount(totalCreated);
        vo.setMergedMrCount(totalMerged);
        vo.setOpenMrCount(totalCreated - totalMerged - totalClosed);
        vo.setAvgMergeHours(avgMergeHours);
        vo.setCiSuccessRate(MathUtils.safeDivide(totalCiSuccess, totalCi, 4)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        vo.setConflictRate(MathUtils.safeDivide(totalConflict, totalCreated, 4)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

        return vo;
    }

    /**
     * 趋势数据：按日或按周分组
     */
    public ReportTrendVO getTrend(LocalDate startDate, LocalDate endDate, String groupBy) {
        boolean byWeek = "week".equalsIgnoreCase(groupBy);

        LambdaQueryWrapper<ReportDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ReportDailySummary::getSummaryDate, startDate)
                .le(ReportDailySummary::getSummaryDate, endDate)
                .orderByAsc(ReportDailySummary::getSummaryDate);
        List<ReportDailySummary> list = reportDailySummaryMapper.selectList(wrapper);

        ReportTrendVO vo = new ReportTrendVO();
        vo.setLabels(new ArrayList<>());
        vo.setCreatedData(new ArrayList<>());
        vo.setMergedData(new ArrayList<>());
        vo.setClosedData(new ArrayList<>());

        if (byWeek) {
            Map<String, List<ReportDailySummary>> weekMap = new LinkedHashMap<>();
            for (ReportDailySummary r : list) {
                String weekLabel = formatWeekLabel(r.getSummaryDate());
                weekMap.computeIfAbsent(weekLabel, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<String, List<ReportDailySummary>> entry : weekMap.entrySet()) {
                vo.getLabels().add(entry.getKey());
                vo.getCreatedData().add(entry.getValue().stream().mapToInt(ReportDailySummary::getCreatedCount).sum());
                vo.getMergedData().add(entry.getValue().stream().mapToInt(ReportDailySummary::getMergedCount).sum());
                vo.getClosedData().add(entry.getValue().stream().mapToInt(ReportDailySummary::getClosedCount).sum());
            }
        } else {
            for (ReportDailySummary r : list) {
                vo.getLabels().add(r.getSummaryDate().toString());
                vo.getCreatedData().add(r.getCreatedCount());
                vo.getMergedData().add(r.getMergedCount());
                vo.getClosedData().add(r.getClosedCount());
            }
        }
        return vo;
    }

    /**
     * 分布数据：项目 / 作者 / 状态
     */
    public ReportDistributionVO getDistribution(String type) {
        ReportDistributionVO vo = new ReportDistributionVO();
        vo.setLabels(new ArrayList<>());
        vo.setValues(new ArrayList<>());

        switch (type) {
            case "project" -> {
                List<Map<String, Object>> rows = mrsMapper.selectProjectDistribution();
                for (Map<String, Object> row : rows) {
                    vo.getLabels().add(String.valueOf(row.get("projectName")));
                    vo.getValues().add(((Number) row.get("count")).intValue());
                }
            }
            case "author" -> {
                List<Map<String, Object>> rows = mrsMapper.selectAuthorDistribution();
                for (Map<String, Object> row : rows) {
                    vo.getLabels().add(String.valueOf(row.get("authorName")));
                    vo.getValues().add(((Number) row.get("count")).intValue());
                }
            }
            case "status" -> {
                List<Map<String, Object>> rows = mrsMapper.selectStatusDistribution();
                for (Map<String, Object> row : rows) {
                    vo.getLabels().add(String.valueOf(row.get("boardStatus")));
                    vo.getValues().add(((Number) row.get("count")).intValue());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported distribution type: " + type);
        }
        return vo;
    }

    /**
     * Excel 流式导出
     */
    public void exportExcel(ServletOutputStream outputStream) throws IOException {
        com.alibaba.excel.ExcelWriter writer = com.alibaba.excel.EasyExcel
                .write(outputStream, MrExportDTO.class)
                .build();
        com.alibaba.excel.write.metadata.WriteSheet sheet = new com.alibaba.excel.write.metadata.WriteSheet();
        sheet.setSheetName("MR列表");

        int pageSize = 500;
        int page = 1;
        boolean hasMore = true;
        while (hasMore) {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Mrs> pageParam =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
            mrsMapper.selectPage(pageParam, null);
            List<Mrs> records = pageParam.getRecords();
            if (records.isEmpty()) {
                hasMore = false;
                break;
            }
            List<MrExportDTO> dtos = records.stream().map(this::convertToExportDTO).toList();
            writer.write(dtos, sheet);
            hasMore = records.size() == pageSize;
            page++;
        }
        writer.finish();
    }

    /**
     * CSV 导出（UTF-8 BOM）
     */
    public void exportCsv(ServletOutputStream outputStream) throws IOException {
        // UTF-8 BOM
        outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (Writer w = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            w.write("MR标题,项目,作者,源分支,目标分支,看板状态,CI状态,是否有冲突,创建时间,更新时间,合并时间,Web链接\n");

            int pageSize = 500;
            int page = 1;
            boolean hasMore = true;
            while (hasMore) {
                com.baomidou.mybatisplus.extension.plugins.pagination.Page<Mrs> pageParam =
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
                mrsMapper.selectPage(pageParam, null);
                List<Mrs> records = pageParam.getRecords();
                if (records.isEmpty()) {
                    hasMore = false;
                    break;
                }
                for (Mrs mr : records) {
                    w.write(escapeCsv(mr.getTitle()) + ",");
                    w.write(escapeCsv(getProjectName(mr.getProjectId())) + ",");
                    w.write(escapeCsv(mr.getAuthorName()) + ",");
                    w.write(escapeCsv(mr.getSourceBranch()) + ",");
                    w.write(escapeCsv(mr.getTargetBranch()) + ",");
                    w.write(escapeCsv(mr.getBoardStatus()) + ",");
                    w.write(escapeCsv(mr.getCiStatus()) + ",");
                    w.write(escapeCsv(mr.getHasConflict() != null && mr.getHasConflict() ? "是" : "否") + ",");
                    w.write(escapeCsv(formatDateTime(mr.getCreatedAt())) + ",");
                    w.write(escapeCsv(formatDateTime(mr.getUpdatedAt())) + ",");
                    w.write(escapeCsv(formatDateTime(mr.getMergedAt())) + ",");
                    w.write(escapeCsv(mr.getWebUrl()) + "\n");
                }
                w.flush();
                hasMore = records.size() == pageSize;
                page++;
            }
        }
    }

    private MrExportDTO convertToExportDTO(Mrs mr) {
        MrExportDTO dto = new MrExportDTO();
        dto.setTitle(mr.getTitle());
        dto.setProjectName(getProjectName(mr.getProjectId()));
        dto.setAuthorName(mr.getAuthorName());
        dto.setSourceBranch(mr.getSourceBranch());
        dto.setTargetBranch(mr.getTargetBranch());
        dto.setBoardStatus(mr.getBoardStatus());
        dto.setCiStatus(mr.getCiStatus());
        dto.setHasConflict(mr.getHasConflict() != null && mr.getHasConflict() ? "是" : "否");
        dto.setCreatedAt(formatDateTime(mr.getCreatedAt()));
        dto.setUpdatedAt(formatDateTime(mr.getUpdatedAt()));
        dto.setMergedAt(formatDateTime(mr.getMergedAt()));
        dto.setWebUrl(mr.getWebUrl());
        return dto;
    }

    private String getProjectName(Long projectId) {
        if (projectId == null) return "";
        var project = projectMapper.selectById(projectId);
        return project != null ? project.getName() : "";
    }

    private String formatDateTime(java.time.LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\n") || v.contains("\"")) {
            v = "\"" + v + "\"";
        }
        return v;
    }

    private String formatWeekLabel(LocalDate date) {
        WeekFields wf = WeekFields.of(Locale.getDefault());
        int week = date.get(wf.weekOfWeekBasedYear());
        int year = date.get(wf.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }
}
