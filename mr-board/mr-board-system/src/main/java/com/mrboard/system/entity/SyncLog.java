package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_logs")
public class SyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long gitSourceId;
    private String syncType;
    private String triggerType;
    private String status;
    private Integer mrCount;
    private Integer ciCount;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
