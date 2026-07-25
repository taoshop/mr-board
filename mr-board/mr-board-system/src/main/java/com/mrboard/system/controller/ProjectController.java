package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.ProjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "项目管理", description = "项目列表查询、项目CRUD（仅ADMIN）")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    @Operation(summary = "项目列表")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<Project>> list(@Parameter(description = "Git源ID") @RequestParam(required = false) Long gitSourceId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (gitSourceId != null) {
            wrapper.eq(Project::getGitSourceId, gitSourceId);
        }
        wrapper.orderByDesc(Project::getCreatedAt);
        return Result.success(projectMapper.selectList(wrapper));
    }

    @Operation(summary = "创建项目（ADMIN）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody Project project) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return Result.success(null);
    }

    @Operation(summary = "更新项目（ADMIN）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@Parameter(description = "项目ID") @PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return Result.success(null);
    }

    @Operation(summary = "删除项目（ADMIN）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@Parameter(description = "项目ID") @PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.success(null);
    }
}
