package com.mrboard.system.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CiDTO {
    private String platformJobId;
    private String name;
    private String stage;
    private String status;
    private String logUrl;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
