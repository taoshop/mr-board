package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mrs")
public class Mrs {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long platformMrId;
    private String title;
    private String description;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private Long assigneeId;
    private String assigneeName;
    private String sourceBranch;
    private String targetBranch;
    private String platformStatus;
    private String boardStatus;
    private String ciStatus;
    private Boolean hasConflict;
    private Boolean mergeable;
    private Integer changesCount;
    private Integer additions;
    private Integer deletions;
    private Integer commentsCount;
    private String labels;
    private String webUrl;
    private String reviewers;
    private String approvalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime mergedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastSyncAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime localUpdatedAt;
}
