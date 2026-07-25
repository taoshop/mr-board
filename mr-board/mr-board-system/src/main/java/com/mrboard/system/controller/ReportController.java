package com.mrboard.system.controller;

import com.mrboard.common.result.Result;
import com.mrboard.system.service.ReportService;
import com.mrboard.system.vo.report.ReportDistributionVO;
import com.mrboard.system.vo.report.ReportOverviewVO;
import com.mrboard.system.vo.report.ReportTrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Tag(name = "报表", description = "统计报表：概览、趋势、分布")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "概览指标", description = "支持周或月维度查询")
    @GetMapping("/overview")
    @Cacheable(value = "report:overview", key = "#week ?: #month ?: 'current'")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<ReportOverviewVO> overview(
            @Parameter(description = "周维度，如 2024-W01") @RequestParam(required = false) String week,
            @Parameter(description = "月维度，如 2024-01") @RequestParam(required = false) String month
    ) {
        LocalDate start;
        LocalDate end;
        if (week != null && !week.isBlank()) {
            String[] parts = week.split("-W");
            int year = Integer.parseInt(parts[0]);
            int weekNum = Integer.parseInt(parts[1]);
            WeekFields wf = WeekFields.of(Locale.getDefault());
            start = LocalDate.ofYearDay(year, 1)
                    .with(wf.weekOfWeekBasedYear(), weekNum)
                    .with(wf.dayOfWeek(), 1L);
            end = start.plusDays(6);
        } else if (month != null && !month.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
            start = LocalDate.parse(month + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else {
            // 默认本周
            start = LocalDate.now().with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1L);
            end = start.plusDays(6);
        }
        return Result.success(reportService.getOverview(start, end));
    }

    @Operation(summary = "趋势数据", description = "按日或按周分组返回 MR 创建/合并/关闭趋势")
    @GetMapping("/trend")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<ReportTrendVO> trend(
            @Parameter(description = "开始日期 yyyy-MM-dd") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "结束日期 yyyy-MM-dd") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @Parameter(description = "分组维度：day / week") @RequestParam(defaultValue = "day") String groupBy
    ) {
        return Result.success(reportService.getTrend(start, end, groupBy));
    }

    @Operation(summary = "分布数据", description = "项目 / 作者 / 状态分布")
    @GetMapping("/distribution")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<ReportDistributionVO> distribution(
            @Parameter(description = "分布类型：project / author / status") @RequestParam String type
    ) {
        return Result.success(reportService.getDistribution(type));
    }

    @Operation(summary = "导出 Excel", description = "流式导出全部 MR 数据")
    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public void exportExcel(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("MR列表_" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        reportService.exportExcel(response.getOutputStream());
    }

    @Operation(summary = "导出 CSV", description = "UTF-8 BOM 导出全部 MR 数据")
    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public void exportCsv(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("MR列表_" + LocalDate.now() + ".csv", StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        reportService.exportCsv(response.getOutputStream());
    }
}
