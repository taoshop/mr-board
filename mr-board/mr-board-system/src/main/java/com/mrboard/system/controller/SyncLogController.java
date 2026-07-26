package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.SyncLog;
import com.mrboard.system.mapper.SyncLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Tag(name = "同步日志", description = "同步任务执行日志查询（PM/TECHLEAD/ADMIN）")
@RestController
@RequestMapping("/api/admin/sync/logs")
@RequiredArgsConstructor
public class SyncLogController {

    private final SyncLogMapper syncLogMapper;

    @Operation(summary = "同步日志分页列表", description = "支持按 Git源、项目、状态、时间范围筛选")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<Page<SyncLog>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Git源ID") @RequestParam(required = false) Long gitSourceId,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "同步状态") @RequestParam(required = false) String status,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd") @RequestParam(required = false) String endDate
    ) {
        LambdaQueryWrapper<SyncLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SyncLog::getCreatedAt);
        if (gitSourceId != null) {
            wrapper.eq(SyncLog::getGitSourceId, gitSourceId);
        }
        if (projectId != null) {
            wrapper.eq(SyncLog::getProjectId, projectId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SyncLog::getStatus, status);
        }
        if (startDate != null && !startDate.isEmpty()) {
            try {
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                wrapper.ge(SyncLog::getCreatedAt, start);
            } catch (DateTimeParseException e) {
                return Result.error(400, "开始日期格式错误，应为 yyyy-MM-dd");
            }
        }
        if (endDate != null && !endDate.isEmpty()) {
            try {
                LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
                wrapper.le(SyncLog::getCreatedAt, end);
            } catch (DateTimeParseException e) {
                return Result.error(400, "结束日期格式错误，应为 yyyy-MM-dd");
            }
        }
        Page<SyncLog> result = syncLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }
}
