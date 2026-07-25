-- mr_status_history 表 DDL
-- 用于记录 MR 看板状态变更审计日志

CREATE TABLE IF NOT EXISTS mr_status_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    mr_id           BIGINT NOT NULL COMMENT '关联的MR ID（mrs表）',
    from_status     VARCHAR(32) COMMENT '变更前看板状态',
    to_status       VARCHAR(32) NOT NULL COMMENT '变更后看板状态',
    operator_id     BIGINT COMMENT '操作人用户ID',
    operator_name   VARCHAR(64) COMMENT '操作人用户名',
    operator_ip     VARCHAR(64) COMMENT '操作人IP地址',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',

    INDEX idx_mr_id (mr_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MR状态变更历史记录表';
