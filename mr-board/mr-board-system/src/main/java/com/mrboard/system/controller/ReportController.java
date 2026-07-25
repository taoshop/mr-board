package com.mrboard.system.controller;

import com.mrboard.common.result.Result;
import com.mrboard.system.export.ExportTask;
import com.mrboard.system.export.ExportTaskManager;
import com.mrboard.system.service.AsyncExportService;
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
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "报表", description = "统计报表：概览、趋势、分布")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AsyncExportService asyncExportService;
    private final ExportTaskManager exportTaskManager;

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

    @Operation(summary = "异步提交导出任务", description = "type: excel / csv")
    @PostMapping("/export/async")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<ExportTask> exportAsync(@RequestParam String type) {
        if (!"excel".equalsIgnoreCase(type) && !"csv".equalsIgnoreCase(type)) {
            return Result.error(400, "不支持的导出类型");
        }
        ExportTask task = exportTaskManager.createTask(type.toLowerCase());
        if ("excel".equalsIgnoreCase(type)) {
            asyncExportService.exportExcelAsync(task.getId());
        } else {
            asyncExportService.exportCsvAsync(task.getId());
        }
        return Result.success(task);
    }

    @Operation(summary = "报表明细数据", description = "按日期范围查询 MR 明细列表，支持下钻到看板")
    @GetMapping("/detail")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<List<Map<String, Object>>> detail(
            @Parameter(description = "开始日期 yyyy-MM-dd") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "结束日期 yyyy-MM-dd") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId
    ) {
        return Result.success(reportService.getDetail(start, end, projectId));
    }

    @Operation(summary = "查询导出任务状态")
    @GetMapping("/export/status/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<ExportTask> exportStatus(@PathVariable String taskId) {
        ExportTask task = exportTaskManager.getTask(taskId);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(task);
    }

    @Operation(summary = "下载导出的文件")
    @GetMapping("/export/download/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public void exportDownload(@PathVariable String taskId, HttpServletResponse response) throws IOException {
        ExportTask task = exportTaskManager.getTask(taskId);
        if (task == null || task.getFilePath() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("文件不存在");
            return;
        }
        File file = new File(task.getFilePath());
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("文件不存在");
            return;
        }
        String ext = "excel".equals(task.getType()) ? "xlsx" : "csv";
        String fileName = URLEncoder.encode("MR列表_" + LocalDate.now() + "." + ext, StandardCharsets.UTF_8);
        String contentType = "excel".equals(task.getType())
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv;charset=UTF-8";
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.transferTo(response.getOutputStream());
        }
    }
}
