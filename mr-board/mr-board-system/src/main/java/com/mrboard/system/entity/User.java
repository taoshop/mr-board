package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String email;
    private String displayName;
    private String avatar;
    private String department;
    private String platformUsername;

    @TableField("password_changed")
    private Boolean passwordChanged;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 用户角色列表（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private List<String> roles;
}
