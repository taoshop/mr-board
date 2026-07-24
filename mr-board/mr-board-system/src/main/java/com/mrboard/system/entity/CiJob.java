package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ci_jobs")
public class CiJob {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long platformMrId;
    private String platformJobId;
    private String name;
    private String stage;
    private String status;
    private String logUrl;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
