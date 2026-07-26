package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.User;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "看板", description = "看板列定义、看板数据、MR的CI记录")
@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final MrsMapper mrsMapper;
    private final ProjectMapper projectMapper;
    private final CiJobMapper ciJobMapper;
    private final UserMapper userMapper;

    private static final List<String> COLUMNS = Arrays.asList(
            "pending_review", "reviewing", "ci_checking", "conflict", "ready", "merged", "closed"
    );

    @Operation(summary = "看板列定义")
    @GetMapping("/columns")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<Map<String, Object>>> getColumns() {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String key : COLUMNS) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("key", key);
            col.put("label", labelOf(key));
            col.put("color", colorOf(key));
            columns.add(col);
        }
        return Result.success(columns);
    }

    @Operation(summary = "看板数据", description = "按7列分组返回MR列表，支持项目、状态、作者、分支筛选；status支持逗号分隔多选")
    @GetMapping
    @Cacheable(value = "board", key = "'project:' + (#projectId != null ? #projectId : 'all') + ':status:' + (#status != null ? #status : 'all') + ':author:' + (#author != null ? #author : 'all') + ':branch:' + (#branch != null ? #branch : 'all')")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<Map<String, List<Mrs>>> getBoard(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "看板状态，支持逗号分隔多选") @RequestParam(required = false) String status,
            @Parameter(description = "作者用户名") @RequestParam(required = false) String author,
            @Parameter(description = "目标分支") @RequestParam(required = false) String branch
    ) {
        LambdaQueryWrapper<Mrs> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(Mrs::getProjectId, projectId);
        }
        if (StringUtils.isNotBlank(status)) {
            List<String> statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !"all".equalsIgnoreCase(s))
                    .collect(Collectors.toList());
            if (statuses.size() == 1) {
                wrapper.eq(Mrs::getBoardStatus, statuses.get(0));
            } else if (statuses.size() > 1) {
                wrapper.in(Mrs::getBoardStatus, statuses);
            }
        }
        if (StringUtils.isNotBlank(author) && !"all".equalsIgnoreCase(author)) {
            wrapper.eq(Mrs::getAuthorName, author);
        }
        if (StringUtils.isNotBlank(branch) && !"all".equalsIgnoreCase(branch)) {
            wrapper.eq(Mrs::getTargetBranch, branch);
        }

        // 数据级权限：DEVELOPER 仅看自己 MR
        applyDataScope(wrapper);

        wrapper.orderByDesc(Mrs::getUpdatedAt);
        List<Mrs> list = mrsMapper.selectList(wrapper);

        Map<String, List<Mrs>> grouped = new LinkedHashMap<>();
        for (String col : COLUMNS) {
            grouped.put(col, new ArrayList<>());
        }
        for (Mrs mr : list) {
            String bs = mr.getBoardStatus();
            if (bs == null || !grouped.containsKey(bs)) {
                bs = "pending_review";
            }
            grouped.get(bs).add(mr);
        }
        return Result.success(grouped);
    }

    @Operation(summary = "看板项目列表")
    @GetMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<Project>> listProjects() {
        List<Project> list = projectMapper.selectList(null);
        return Result.success(list);
    }

    @Operation(summary = "MR的CI记录", description = "根据平台MR ID查询关联的CI任务")
    @GetMapping("/mr/{id}/ci")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<CiJob>> getMrCi(@Parameter(description = "平台MR ID") @PathVariable Long id) {
        LambdaQueryWrapper<CiJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CiJob::getPlatformMrId, id).orderByAsc(CiJob::getStartedAt);
        return Result.success(ciJobMapper.selectList(wrapper));
    }

    private String labelOf(String key) {
        return switch (key) {
            case "pending_review" -> "待 Review";
            case "reviewing" -> "Review 中";
            case "ci_checking" -> "CI 检查中";
            case "conflict" -> "冲突待解决";
            case "ready" -> "可合并";
            case "merged" -> "已合并";
            case "closed" -> "已关闭";
            default -> key;
        };
    }

    private String colorOf(String key) {
        return switch (key) {
            case "pending_review" -> "#909399";
            case "reviewing" -> "#e6a23c";
            case "ci_checking" -> "#409eff";
            case "conflict" -> "#f56c6c";
            case "ready" -> "#67c23a";
            case "merged" -> "#409eff";
            case "closed" -> "#909399";
            default -> "#909399";
        };
    }

    private void applyDataScope(LambdaQueryWrapper<Mrs> wrapper) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isPm = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PM"));
        boolean isTechlead = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TECHLEAD"));
        if (isAdmin || isPm || isTechlead) {
            return;
        }
        // DEVELOPER / REVIEWER 仅看自己创建的 MR
        String userId = authentication.getName();
        User currentUser = userMapper.selectById(Long.valueOf(userId));
        if (currentUser != null && currentUser.getPlatformUsername() != null) {
            wrapper.eq(Mrs::getAuthorName, currentUser.getPlatformUsername());
        } else if (currentUser != null) {
            wrapper.eq(Mrs::getAuthorName, currentUser.getUsername());
        }
    }
}
