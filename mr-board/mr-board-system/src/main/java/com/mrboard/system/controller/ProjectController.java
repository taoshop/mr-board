package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<Project>> list(@RequestParam(required = false) Long gitSourceId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (gitSourceId != null) {
            wrapper.eq(Project::getGitSourceId, gitSourceId);
        }
        wrapper.orderByDesc(Project::getCreatedAt);
        return Result.success(projectMapper.selectList(wrapper));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody Project project) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.success(null);
    }
}
