package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gitSourceId;
    private String platformProjectId;
    private String name;
    @TableField("path")
    private String projectPath;
    private String webUrl;
    private Integer isActive;
    private LocalDateTime lastSyncAt;
    private Integer mrCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
