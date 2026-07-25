package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mr_comments")
public class MrComment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mrId;
    private String platformCommentId;
    private String authorName;
    private String authorAvatar;
    private String body;
    private Integer isSystem;
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime localUpdatedAt;
}
