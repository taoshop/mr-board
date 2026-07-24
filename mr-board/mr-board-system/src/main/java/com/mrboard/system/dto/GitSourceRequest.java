package com.mrboard.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GitSourceRequest {
    @NotBlank(message = "名称不能为空")
    private String name;

    @NotNull(message = "平台类型不能为空")
    private Integer platformType;

    @NotBlank(message = "API地址不能为空")
    private String apiBaseUrl;

    @NotBlank(message = "Token不能为空")
    private String accessToken;

    private String syncCron;
    private Integer isActive;
    private List<String> projectPaths;
}
