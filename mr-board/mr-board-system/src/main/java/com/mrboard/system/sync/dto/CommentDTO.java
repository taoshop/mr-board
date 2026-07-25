package com.mrboard.system.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentDTO {
    private String platformCommentId;
    private String authorName;
    private String authorAvatar;
    private String body;
    private Boolean isSystem;
    private LocalDateTime createdAt;
}
