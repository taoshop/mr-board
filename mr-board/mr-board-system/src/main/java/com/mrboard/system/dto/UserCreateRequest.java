package com.mrboard.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    private String displayName;
    private String avatar;
    private String department;
    private List<Long> roleIds;
}
