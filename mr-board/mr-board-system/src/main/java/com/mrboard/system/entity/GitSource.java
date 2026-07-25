package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("git_sources")
public class GitSource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer platformType;
    private String apiBaseUrl;
    private String accessToken;
    private String syncCron;
    private String webhookSecret;
    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
