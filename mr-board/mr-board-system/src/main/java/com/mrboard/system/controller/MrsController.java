package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrboard.common.result.Result;
import com.mrboard.common.utils.AesUtil;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.entity.MrStatusHistory;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.User;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.GitSourceMapper;
import com.mrboard.system.mapper.MrStatusHistoryMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.UserMapper;
import com.mrboard.system.sync.GitClientFactory;
import com.mrboard.system.sync.GitSyncClient;
import com.mrboard.system.sync.dto.ChangeDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "MR管理", description = "MR列表查询、MR详情、MR关联CI记录")
@RestController
@RequestMapping("/api/mrs")
@RequiredArgsConstructor
public class MrsController {

    private final MrsMapper mrsMapper;
    private final CiJobMapper ciJobMapper;
    private final MrStatusHistoryMapper historyMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final GitSourceMapper gitSourceMapper;
    private final GitClientFactory gitClientFactory;
    private final AesUtil aesUtil;

    @Operation(summary = "MR列表查询", description = "支持按项目、看板状态、作者、目标分支筛选，支持分页")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<Page<Mrs>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "看板状态") @RequestParam(required = false) String boardStatus,
            @Parameter(description = "作者用户名") @RequestParam(required = false) String author,
            @Parameter(description = "目标分支") @RequestParam(required = false) String branch
    ) {
        LambdaQueryWrapper<Mrs> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(Mrs::getProjectId, projectId);
        }
        if (boardStatus != null && !boardStatus.isEmpty() && !"all".equalsIgnoreCase(boardStatus)) {
            wrapper.eq(Mrs::getBoardStatus, boardStatus);
        }
        if (author != null && !author.isEmpty() && !"all".equalsIgnoreCase(author)) {
            wrapper.eq(Mrs::getAuthorName, author);
        }
        if (branch != null && !branch.isEmpty() && !"all".equalsIgnoreCase(branch)) {
            wrapper.eq(Mrs::getTargetBranch, branch);
        }
        wrapper.orderByDesc(Mrs::getUpdatedAt);
        Page<Mrs> result = mrsMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @Operation(summary = "MR详情", description = "根据MR主键ID获取详情，并附带最近10条CI记录")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<MrsDetailVO> getById(
            @Parameter(description = "MR主键ID") @PathVariable Long id
    ) {
        Mrs mr = mrsMapper.selectById(id);
        if (mr == null) {
            return Result.error(404, "MR不存在");
        }
        LambdaQueryWrapper<CiJob> ciWrapper = new LambdaQueryWrapper<>();
        ciWrapper.eq(CiJob::getPlatformMrId, mr.getPlatformMrId())
                .orderByDesc(CiJob::getStartedAt)
                .last("LIMIT 10");
        List<CiJob> ciJobs = ciJobMapper.selectList(ciWrapper);

        LambdaQueryWrapper<MrStatusHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(MrStatusHistory::getMrId, mr.getId())
                .orderByDesc(MrStatusHistory::getCreatedAt);
        List<MrStatusHistory> statusHistory = historyMapper.selectList(historyWrapper);

        MrsDetailVO vo = new MrsDetailVO();
        vo.setMr(mr);
        vo.setCiJobs(ciJobs);
        vo.setStatusHistory(statusHistory);
        return Result.success(vo);
    }

    @lombok.Data
    public static class MrsDetailVO {
        private Mrs mr;
        private List<CiJob> ciJobs;
        private List<MrStatusHistory> statusHistory;
    }

    @Operation(summary = "MR变更文件列表", description = "从Git平台实时拉取该MR的diff文件列表")
    @GetMapping("/{id}/changes")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<ChangeDTO>> getChanges(
            @Parameter(description = "MR主键ID") @PathVariable Long id) {
        Mrs mr = mrsMapper.selectById(id);
        if (mr == null) {
            return Result.error(404, "MR不存在");
        }
        Project project = projectMapper.selectById(mr.getProjectId());
        if (project == null) {
            return Result.error(404, "项目不存在");
        }
        GitSource source = gitSourceMapper.selectById(project.getGitSourceId());
        if (source == null) {
            return Result.error(500, "Git源配置缺失");
        }
        try {
            String token = aesUtil.decrypt(source.getAccessToken());
            GitSyncClient client = gitClientFactory.create(source.getPlatformType(), source.getApiBaseUrl(), token);
            List<ChangeDTO> changes = client.fetchChanges(project.getProjectPath(), mr.getPlatformMrId());
            return Result.success(changes);
        } catch (Exception e) {
            return Result.error(500, "获取变更文件失败: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class StatusUpdateRequest {
        @NotBlank
        private String boardStatus;
    }

    @Operation(summary = "更新MR看板状态", description = "拖拽状态流转，含业务规则校验与权限控制")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER')")
    @CacheEvict(value = "board", allEntries = true)
    public Result<Void> updateStatus(
            @Parameter(description = "MR主键ID") @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            HttpServletRequest httpRequest) {
        Mrs mr = mrsMapper.selectById(id);
        if (mr == null) {
            return Result.error(404, "MR不存在");
        }

        String newStatus = request.getBoardStatus();
        String oldStatus = mr.getBoardStatus();
        if (oldStatus != null && oldStatus.equals(newStatus)) {
            return Result.success();
        }

        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }

        // 权限与业务规则校验
        String error = validateStatusTransition(mr, newStatus, currentUser);
        if (error != null) {
            return Result.error(409, error);
        }

        // merged / closed 需调用 Git API
        if ("merged".equals(newStatus) || "closed".equals(newStatus)) {
            String apiError = callGitApi(mr, newStatus);
            if (apiError != null) {
                return Result.error(500, apiError);
            }
            mr.setPlatformStatus(newStatus);
            if ("merged".equals(newStatus)) {
                mr.setMergedAt(LocalDateTime.now());
            } else {
                mr.setClosedAt(LocalDateTime.now());
            }
        }

        mr.setBoardStatus(newStatus);
        mrsMapper.updateById(mr);

        recordHistory(mr.getId(), oldStatus, newStatus, currentUser, httpRequest.getRemoteAddr());
        return Result.success();
    }

    private String callGitApi(Mrs mr, String newStatus) {
        Project project = projectMapper.selectById(mr.getProjectId());
        if (project == null) {
            return "项目不存在，无法执行Git操作";
        }
        GitSource source = gitSourceMapper.selectById(project.getGitSourceId());
        if (source == null) {
            return "Git源配置缺失，无法执行Git操作";
        }
        try {
            String token = aesUtil.decrypt(source.getAccessToken());
            GitSyncClient client = gitClientFactory.create(source.getPlatformType(), source.getApiBaseUrl(), token);
            boolean success;
            if ("merged".equals(newStatus)) {
                success = client.mergeMR(project.getProjectPath(), mr.getPlatformMrId());
            } else {
                success = client.closeMR(project.getProjectPath(), mr.getPlatformMrId());
            }
            if (!success) {
                return "Git平台操作失败，请检查Token权限或MR状态";
            }
            return null;
        } catch (Exception e) {
            return "Git平台操作异常: " + e.getMessage();
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String userId = authentication.getName();
        return userMapper.selectById(Long.valueOf(userId));
    }

    private String validateStatusTransition(Mrs mr, String newStatus, User currentUser) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isTechlead = hasRole(authentication, "ROLE_TECHLEAD");
        boolean isPm = hasRole(authentication, "ROLE_PM");
        boolean isDeveloper = hasRole(authentication, "ROLE_DEVELOPER");

        // REVIEWER 无拖拽权限（@PreAuthorize 已过滤，此处防御性校验）
        if (hasRole(authentication, "ROLE_REVIEWER") && !isAdmin && !isTechlead && !isPm) {
            return "当前角色无权限操作MR状态";
        }

        // merged / closed 仅 ADMIN / TECHLEAD 可操作
        if (("merged".equals(newStatus) || "closed".equals(newStatus)) && !isAdmin && !isTechlead) {
            return "仅 ADMIN 或 TECHLEAD 可将MR设为已合并/已关闭";
        }

        // 冲突MR禁止拖入 ready / merged
        if (Boolean.TRUE.equals(mr.getHasConflict()) && ("ready".equals(newStatus) || "merged".equals(newStatus))) {
            return "当前MR存在冲突，请先解决冲突后再操作";
        }

        // DEVELOPER 只能操作自己的 MR
        if (isDeveloper && !isAdmin && !isTechlead && !isPm) {
            String operatorName = currentUser.getPlatformUsername() != null ? currentUser.getPlatformUsername() : currentUser.getUsername();
            String authorName = mr.getAuthorName();
            if (authorName == null || !authorName.equals(operatorName)) {
                return "只能操作自己提交的MR";
            }
        }

        return null;
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private void recordHistory(Long mrId, String fromStatus, String toStatus, User operator, String ip) {
        MrStatusHistory history = new MrStatusHistory();
        history.setMrId(mrId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operator.getId());
        history.setOperatorName(operator.getUsername());
        history.setOperatorIp(ip);
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }
}
