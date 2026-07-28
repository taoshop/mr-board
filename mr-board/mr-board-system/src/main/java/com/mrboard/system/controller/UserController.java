package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrboard.common.result.Result;
import com.mrboard.system.dto.UserCreateRequest;
import com.mrboard.system.entity.Role;
import com.mrboard.system.entity.User;
import com.mrboard.system.entity.UserRole;
import com.mrboard.system.mapper.RoleMapper;
import com.mrboard.system.mapper.UserMapper;
import com.mrboard.system.mapper.UserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "用户管理", description = "用户CRUD、角色分配（仅ADMIN）")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "用户分页列表")
    @GetMapping
    public Result<Page<User>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "用户名/显示名关键字") @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(User::getUsername, keyword)
                    .or()
                    .like(User::getDisplayName, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 填充每个用户的角色信息
        for (User user : result.getRecords()) {
            List<Role> roles = userMapper.selectRolesByUserId(user.getId());
            user.setRoles(roles.stream().map(Role::getName).collect(Collectors.toList()));
        }
        return Result.success(result);
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<User> getById(@Parameter(description = "用户ID") @PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(1001, "用户不存在");
        }
        return Result.success(user);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @Transactional
    public Result<Void> create(@Valid @RequestBody UserCreateRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            return Result.error(1006, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "Password123"));
        user.setPasswordChanged(false);
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setAvatar(request.getAvatar());
        user.setDepartment(request.getDepartment());
        userMapper.insert(user);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            for (Long roleId : request.getRoleIds()) {
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }

        return Result.success();
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        List<Role> roles = roleMapper.selectList(null);
        return Result.success(roles);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    @Transactional
    public Result<Void> update(@Parameter(description = "用户ID") @PathVariable Long id, @Valid @RequestBody UserCreateRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(1001, "用户不存在");
        }

        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setAvatar(request.getAvatar());
        user.setDepartment(request.getDepartment());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setPasswordChanged(false);
        }
        userMapper.updateById(user);

        // 更新角色关联
        if (request.getRoleIds() != null) {
            userRoleMapper.delete(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id)
            );
            for (Long roleId : request.getRoleIds()) {
                UserRole ur = new UserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }

        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "用户ID") @PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestParam List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            userMapper.deleteBatchIds(ids);
        }
        return Result.success();
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/{id}/roles")
    @Transactional
    public Result<Void> assignRoles(@Parameter(description = "用户ID") @PathVariable Long id, @RequestBody List<Long> roleIds) {
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id)
        );
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return Result.success();
    }
}
