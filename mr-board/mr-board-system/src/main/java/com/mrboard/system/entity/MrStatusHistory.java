package com.mrboard.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mr_status_history")
public class MrStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mrId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String operatorIp;

    private LocalDateTime createdAt;
}
