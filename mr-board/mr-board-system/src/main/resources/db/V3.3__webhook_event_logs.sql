-- webhook_event_logs 表 DDL
-- 用于记录 Git 平台 Webhook 接收日志，便于排查问题

CREATE TABLE IF NOT EXISTS webhook_event_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    platform_type   VARCHAR(16) NOT NULL COMMENT '平台类型：gitlab / github',
    event_type      VARCHAR(64) NOT NULL COMMENT '事件类型：merge_request / push / pipeline 等',
    project_path    VARCHAR(255) COMMENT '项目路径',
    payload         TEXT COMMENT '请求体 JSON（脱敏后）',
    signature       VARCHAR(512) COMMENT '收到的签名或 Token',
    ip_address      VARCHAR(64) COMMENT '来源 IP',
    processed       TINYINT DEFAULT 0 COMMENT '是否已处理：0 否，1 是',
    error_msg       VARCHAR(512) COMMENT '处理错误信息',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',

    INDEX idx_platform_event (platform_type, event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook事件日志表';
