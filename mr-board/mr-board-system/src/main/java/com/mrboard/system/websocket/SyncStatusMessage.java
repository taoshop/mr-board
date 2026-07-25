package com.mrboard.system.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步状态推送消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusMessage {
    private String type;      // sync_started / sync_completed / sync_failed
    private Long projectId;
    private Long gitSourceId;
    private String projectName;
    private String status;    // running / success / failed
    private Integer mrCount;
    private Integer ciCount;
    private String errorMsg;
    private String timestamp;
}
