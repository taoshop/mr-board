package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.SyncLog;
import com.mrboard.system.mapper.SyncLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync-log")
@RequiredArgsConstructor
public class SyncLogController {

    private final SyncLogMapper syncLogMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<Page<SyncLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status
    ) {
        LambdaQueryWrapper<SyncLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SyncLog::getCreatedAt);
        if (projectId != null) {
            wrapper.eq(SyncLog::getProjectId, projectId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SyncLog::getStatus, status);
        }
        Page<SyncLog> result = syncLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }
}
