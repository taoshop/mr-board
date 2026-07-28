package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrboard.common.result.Result;
import com.mrboard.common.utils.AesUtil;
import com.mrboard.system.dto.GitSourceRequest;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.GitSourceMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.service.SyncService;
import com.mrboard.system.sync.GitClientFactory;
import com.mrboard.system.sync.GitSyncClient;
import com.mrboard.system.job.SyncScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Git源管理", description = "Git源CRUD、测试连接、手动同步触发")
@RestController
@RequestMapping("/api/admin/git-sources")
@RequiredArgsConstructor
public class GitSourceController {

    private final GitSourceMapper gitSourceMapper;
    private final ProjectMapper projectMapper;
    private final AesUtil aesUtil;
    private final GitClientFactory clientFactory;
    private final SyncService syncService;
    private final SyncScheduleService syncScheduleService;

    @Operation(summary = "Git源分页列表")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<Page<GitSource>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer size) {
        Page<GitSource> result = gitSourceMapper.selectPage(new Page<>(page, size), null);
        result.getRecords().forEach(this::maskToken);
        return Result.success(result);
    }

    @Operation(summary = "Git源详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<GitSource> getById(@Parameter(description = "Git源ID") @PathVariable Long id) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source != null) {
            maskToken(source);
        }
        return Result.success(source);
    }

    @Operation(summary = "创建Git源")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<Void> create(@Valid @RequestBody GitSourceRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().isBlank()) {
            return Result.error(400, "Token不能为空");
        }
        GitSource source = new GitSource();
        source.setName(request.getName());
        source.setPlatformType(request.getPlatformType());
        source.setApiBaseUrl(request.getApiBaseUrl());
        try {
            source.setAccessToken(aesUtil.encrypt(request.getAccessToken()));
        } catch (Exception e) {
            return Result.error(500, "Token加密失败");
        }
        source.setWebhookSecret(request.getWebhookSecret());
        source.setSyncCron(request.getSyncCron() != null ? request.getSyncCron() : "0 */5 * * * ?");
        source.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
        gitSourceMapper.insert(source);

        if (request.getProjectPaths() != null) {
            for (String path : request.getProjectPaths()) {
                Project project = new Project();
                project.setGitSourceId(source.getId());
                project.setPlatformProjectId(path.length() > 64 ? path.substring(path.lastIndexOf('/') + 1) : path);
                project.setProjectPath(path);
                project.setName(path.substring(path.lastIndexOf('/') + 1));
                project.setIsActive(1);
                projectMapper.insert(project);
            }
        }
        syncScheduleService.schedule(source);
        return Result.success();
    }

    @Operation(summary = "更新Git源")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<Void> update(@Parameter(description = "Git源ID") @PathVariable Long id, @Valid @RequestBody GitSourceRequest request) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source == null) {
            return Result.error(404, "Git源不存在");
        }
        source.setName(request.getName());
        source.setPlatformType(request.getPlatformType());
        source.setApiBaseUrl(request.getApiBaseUrl());
        if (request.getAccessToken() != null && !request.getAccessToken().isEmpty()) {
            try {
                source.setAccessToken(aesUtil.encrypt(request.getAccessToken()));
            } catch (Exception e) {
                return Result.error(500, "Token加密失败");
            }
        }
        if (request.getWebhookSecret() != null) {
            source.setWebhookSecret(request.getWebhookSecret());
        }
        source.setSyncCron(request.getSyncCron());
        source.setIsActive(request.getIsActive());
        gitSourceMapper.updateById(source);

        // 补充新增的项目路径(已存在的跳过)
        if (request.getProjectPaths() != null) {
            for (String path : request.getProjectPaths()) {
                Long exists = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                        .eq(Project::getGitSourceId, id)
                        .eq(Project::getProjectPath, path));
                if (exists == 0) {
                    Project project = new Project();
                    project.setGitSourceId(id);
                    project.setPlatformProjectId(path.length() > 64 ? path.substring(path.lastIndexOf('/') + 1) : path);
                    project.setProjectPath(path);
                    project.setName(path.substring(path.lastIndexOf('/') + 1));
                    project.setIsActive(1);
                    projectMapper.insert(project);
                }
            }
        }
        syncScheduleService.reschedule(id);
        return Result.success();
    }

    @Operation(summary = "删除Git源")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<Void> delete(@Parameter(description = "Git源ID") @PathVariable Long id) {
        syncScheduleService.remove(id);
        gitSourceMapper.deleteById(id);
        projectMapper.delete(new LambdaQueryWrapper<Project>().eq(Project::getGitSourceId, id));
        return Result.success();
    }

    @Operation(summary = "测试Git源连接")
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<String> testConnection(@Parameter(description = "Git源ID") @PathVariable Long id) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source == null) {
            return Result.error(404, "Git源不存在");
        }
        try {
            String token = aesUtil.decrypt(source.getAccessToken());
            GitSyncClient client = clientFactory.create(source.getPlatformType(), source.getApiBaseUrl(), token);
            boolean valid = client.testConnection();
            return valid ? Result.success("连接成功") : Result.error(500, "连接失败");
        } catch (Exception e) {
            return Result.error(500, "连接失败: " + e.getMessage());
        }
    }

    @Operation(summary = "触发同步", description = "支持 full（全量）或 incremental（增量）同步")
    @PostMapping("/{id}/sync")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD')")
    public Result<String> triggerSync(
            @Parameter(description = "Git源ID") @PathVariable Long id,
            @Parameter(description = "同步类型：full / incremental") @RequestParam(defaultValue = "incremental") String type) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source == null) {
            return Result.error(404, "Git源不存在");
        }
        boolean full = "full".equalsIgnoreCase(type);
        syncService.triggerSyncAsync(id, full, "manual");
        return Result.success("同步任务已触发");
    }

    private void maskToken(GitSource source) {
        if (source.getAccessToken() != null) {
            source.setAccessToken("****");
        }
    }
}
