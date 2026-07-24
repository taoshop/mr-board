package com.mrboard.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mrboard.common.result.Result;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final MrsMapper mrsMapper;
    private final ProjectMapper projectMapper;
    private final CiJobMapper ciJobMapper;

    private static final List<String> COLUMNS = Arrays.asList(
            "open", "testing", "ready", "conflict", "merged", "closed", "failed"
    );

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

    @GetMapping
    @Cacheable(value = "board", key = "'project:' + #projectId + ':status:' + (#status != null ? #status : 'all') + ':author:' + (#author != null ? #author : 'all') + ':branch:' + (#branch != null ? #branch : 'all')")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<Map<String, List<Mrs>>> getBoard(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String branch
    ) {
        LambdaQueryWrapper<Mrs> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(Mrs::getProjectId, projectId);
        }
        if (StringUtils.isNotBlank(status) && !"all".equalsIgnoreCase(status)) {
            wrapper.eq(Mrs::getBoardStatus, status);
        }
        if (StringUtils.isNotBlank(author) && !"all".equalsIgnoreCase(author)) {
            wrapper.eq(Mrs::getAuthorName, author);
        }
        if (StringUtils.isNotBlank(branch) && !"all".equalsIgnoreCase(branch)) {
            wrapper.eq(Mrs::getTargetBranch, branch);
        }

        wrapper.orderByDesc(Mrs::getUpdatedAt);
        List<Mrs> list = mrsMapper.selectList(wrapper);

        Map<String, List<Mrs>> grouped = new LinkedHashMap<>();
        for (String col : COLUMNS) {
            grouped.put(col, new ArrayList<>());
        }
        for (Mrs mr : list) {
            String bs = mr.getBoardStatus();
            if (bs == null || !grouped.containsKey(bs)) {
                bs = "ready";
            }
            grouped.get(bs).add(mr);
        }
        return Result.success(grouped);
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<Project>> listProjects() {
        List<Project> list = projectMapper.selectList(null);
        return Result.success(list);
    }

    @GetMapping("/mr/{id}/ci")
    @PreAuthorize("hasAnyRole('ADMIN','PM','TECHLEAD','DEVELOPER','REVIEWER')")
    public Result<List<CiJob>> getMrCi(@PathVariable Long id) {
        LambdaQueryWrapper<CiJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CiJob::getPlatformMrId, id).orderByAsc(CiJob::getStartedAt);
        return Result.success(ciJobMapper.selectList(wrapper));
    }

    private String labelOf(String key) {
        return switch (key) {
            case "open" -> "开发中";
            case "testing" -> "测试中";
            case "ready" -> "可合并";
            case "conflict" -> "冲突";
            case "merged" -> "已合并";
            case "closed" -> "已关闭";
            case "failed" -> "构建失败";
            default -> key;
        };
    }

    private String colorOf(String key) {
        return switch (key) {
            case "open" -> "#909399";
            case "testing" -> "#e6a23c";
            case "ready" -> "#67c23a";
            case "conflict" -> "#f56c6c";
            case "merged" -> "#409eff";
            case "closed" -> "#909399";
            case "failed" -> "#f56c6c";
            default -> "#909399";
        };
    }
}
