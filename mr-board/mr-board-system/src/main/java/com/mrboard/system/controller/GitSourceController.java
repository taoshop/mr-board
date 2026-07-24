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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/git-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GitSourceController {

    private final GitSourceMapper gitSourceMapper;
    private final ProjectMapper projectMapper;
    private final AesUtil aesUtil;
    private final GitClientFactory clientFactory;
    private final SyncService syncService;

    @GetMapping
    public Result<Page<GitSource>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<GitSource> result = gitSourceMapper.selectPage(new Page<>(page, size), null);
        result.getRecords().forEach(this::maskToken);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<GitSource> getById(@PathVariable Long id) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source != null) {
            maskToken(source);
        }
        return Result.success(source);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody GitSourceRequest request) {
        GitSource source = new GitSource();
        source.setName(request.getName());
        source.setPlatformType(request.getPlatformType());
        source.setApiBaseUrl(request.getApiBaseUrl());
        try {
            source.setAccessToken(aesUtil.encrypt(request.getAccessToken()));
        } catch (Exception e) {
            return Result.error(500, "Token加密失败");
        }
        source.setSyncCron(request.getSyncCron() != null ? request.getSyncCron() : "0 */5 * * * ?");
        source.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
        gitSourceMapper.insert(source);

        if (request.getProjectPaths() != null) {
            for (String path : request.getProjectPaths()) {
                Project project = new Project();
                project.setGitSourceId(source.getId());
                project.setProjectPath(path);
                project.setName(path.substring(path.lastIndexOf('/') + 1));
                project.setIsActive(1);
                projectMapper.insert(project);
            }
        }
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody GitSourceRequest request) {
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
        source.setSyncCron(request.getSyncCron());
        source.setIsActive(request.getIsActive());
        gitSourceMapper.updateById(source);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gitSourceMapper.deleteById(id);
        projectMapper.delete(new LambdaQueryWrapper<Project>().eq(Project::getGitSourceId, id));
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<String> testConnection(@PathVariable Long id) {
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

    @PostMapping("/{id}/sync")
    public Result<String> triggerSync(@PathVariable Long id, @RequestParam(defaultValue = "incremental") String type) {
        GitSource source = gitSourceMapper.selectById(id);
        if (source == null) {
            return Result.error(404, "Git源不存在");
        }
        boolean full = "full".equalsIgnoreCase(type);
        syncService.triggerSync(id, full);
        return Result.success("同步任务已触发");
    }

    private void maskToken(GitSource source) {
        if (source.getAccessToken() != null) {
            source.setAccessToken("****");
        }
    }
}
