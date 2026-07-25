package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook_event_logs")
public class WebhookEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platformType;
    private String eventType;
    private String projectPath;
    private String payload;
    private String signature;
    private String ipAddress;
    private Integer processed;
    private String errorMsg;

    private LocalDateTime createdAt;
}
