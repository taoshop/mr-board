package com.mrboard.system.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MrDTO {
    private Long platformMrId;
    private String title;
    private String description;
    private String authorName;
    private String authorAvatar;
    private String assigneeName;
    private List<String> reviewers;
    private String sourceBranch;
    private String targetBranch;
    private String platformStatus;
    private Boolean hasConflict;
    private Boolean mergeable;
    private Integer changesCount;
    private Integer additions;
    private Integer deletions;
    private Integer commentsCount;
    private List<String> labels;
    private String webUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime mergedAt;
    private LocalDateTime closedAt;
    private String ciStatus;
}
