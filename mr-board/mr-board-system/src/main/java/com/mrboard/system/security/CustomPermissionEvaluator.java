package com.mrboard.system.security;

import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final MrsMapper mrsMapper;
    private final ProjectMapper projectMapper;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return false;
        }
        String perm = permission.toString().toLowerCase();
        if (targetDomainObject instanceof Mrs mr) {
            return hasMrsPermission(mr, userId, perm);
        }
        if (targetDomainObject instanceof Project project) {
            return hasProjectPermission(project, userId, perm);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return false;
        }
        String perm = permission.toString().toLowerCase();
        Long id = Long.valueOf(targetId.toString());
        switch (targetType) {
            case "Mrs", "com.mrboard.system.entity.Mrs" -> {
                Mrs mr = mrsMapper.selectById(id);
                return mr != null && hasMrsPermission(mr, userId, perm);
            }
            case "Project", "com.mrboard.system.entity.Project" -> {
                Project project = projectMapper.selectById(id);
                return project != null && hasProjectPermission(project, userId, perm);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean hasMrsPermission(Mrs mr, Long userId, String permission) {
        // 读取权限：作者、被指派人、或项目成员
        if ("read".equals(permission)) {
            return userId.equals(mr.getAuthorId()) || userId.equals(mr.getAssigneeId());
        }
        // 写入/删除权限：仅作者或管理员
        if ("write".equals(permission) || "delete".equals(permission)) {
            return userId.equals(mr.getAuthorId());
        }
        return false;
    }

    private boolean hasProjectPermission(Project project, Long userId, String permission) {
        // 项目无创建者字段，简化：非 ADMIN 只有 read 权限
        return "read".equals(permission);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }

    private Long getCurrentUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
