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

    /** 创建时必填，编辑时留空表示不修改 */
    private String accessToken;

    private String webhookSecret;
    private String syncCron;
    private Integer isActive;
    private List<String> projectPaths;
}
