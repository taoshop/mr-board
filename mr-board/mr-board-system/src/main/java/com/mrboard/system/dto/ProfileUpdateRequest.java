package com.mrboard.system.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String displayName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String avatar;

    private String password;
}
