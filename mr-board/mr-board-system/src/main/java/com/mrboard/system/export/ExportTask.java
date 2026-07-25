package com.mrboard.system.export;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExportTask {
    private String id;
    private String type;
    private ExportTaskStatus status;
    private String filePath;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
