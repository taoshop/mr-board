-- ============================================================
-- V5.2: MR Comments Cache Table
-- Stores MR comments/notes fetched from GitLab/GitHub API
-- ============================================================
CREATE TABLE IF NOT EXISTS mr_comments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
    mr_id               BIGINT       NOT NULL COMMENT '关联的MR主键ID',
    platform_comment_id VARCHAR(64)  NOT NULL COMMENT '平台评论ID',
    author_name         VARCHAR(64)  NOT NULL COMMENT '评论者平台用户名',
    author_avatar       VARCHAR(255) COMMENT '评论者头像URL',
    body                TEXT         NOT NULL COMMENT '评论内容',
    is_system           TINYINT      DEFAULT 0 COMMENT '是否系统评论：0=否, 1=是',
    created_at          DATETIME     NOT NULL COMMENT '平台创建时间',
    local_updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地记录更新时间',
    UNIQUE KEY uk_mr_comment (mr_id, platform_comment_id),
    INDEX idx_mr_id (mr_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MR评论缓存表';
