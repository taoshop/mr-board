-- ============================================================
-- MR Board System - Database Initialization Script
-- Phase 1: RBAC Tables + Seed Data
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS mr_board
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mr_board;

-- ------------------------------------------------------------
-- Table: users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录用户名',
    password        VARCHAR(128) NOT NULL COMMENT '密码（bcrypt加密）',
    email           VARCHAR(128) NOT NULL UNIQUE COMMENT '邮箱',
    display_name    VARCHAR(64)  COMMENT '显示名称',
    avatar          VARCHAR(255) COMMENT '头像URL',
    department      VARCHAR(64)  COMMENT '部门',
    platform_username VARCHAR(64) COMMENT 'Git平台关联用户名',
    password_changed  TINYINT DEFAULT 0 COMMENT '是否已修改初始密码：0=否, 1=是',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除：0=正常, 1=删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- Table: roles
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    code        VARCHAR(32)  NOT NULL UNIQUE COMMENT '角色编码',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ------------------------------------------------------------
-- Table: permissions
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permissions (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    code    VARCHAR(64) NOT NULL UNIQUE COMMENT '权限编码',
    name    VARCHAR(64) NOT NULL COMMENT '权限名称',
    type    TINYINT NOT NULL DEFAULT 2 COMMENT '类型：1=菜单, 2=操作, 3=数据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ------------------------------------------------------------
-- Table: user_roles
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ------------------------------------------------------------
-- Table: role_permissions
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permissions (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id       BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================================
-- Seed Data
-- ============================================================

-- Roles
INSERT INTO roles (code, name, description) VALUES
('dev', 'Developer', '开发人员'),
('reviewer', 'Reviewer', '代码评审员'),
('techlead', 'Tech Lead', '技术负责人'),
('pm', 'PM', '项目经理'),
('admin', 'Admin', '系统管理员');

-- Permissions (Menus + Operations)
INSERT INTO permissions (code, name, type) VALUES
('menu:dashboard', '看板菜单', 1),
('menu:users', '用户管理菜单', 1),
('menu:reports', '统计报表菜单', 1),
('user:read', '用户查看', 2),
('user:create', '用户创建', 2),
('user:update', '用户更新', 2),
('user:delete', '用户删除', 2),
('mr:read', 'MR查看', 2),
('mr:update', 'MR状态更新', 2),
('report:read', '报表查看', 2),
('report:export', '报表导出', 2);

-- Role-Permission Mapping
-- Admin: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin';

-- Tech Lead: dashboard, reports, mr read/update
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'techlead' AND p.code IN ('menu:dashboard','menu:reports','mr:read','mr:update','report:read','report:export');

-- PM: dashboard, reports
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'pm' AND p.code IN ('menu:dashboard','menu:reports','mr:read','report:read','report:export');

-- Dev: dashboard, mr read/update own
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'dev' AND p.code IN ('menu:dashboard','mr:read','mr:update');

-- Reviewer: dashboard, mr read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'reviewer' AND p.code IN ('menu:dashboard','mr:read');

-- Admin user (password: Admin@123)
-- NOTE: The password below is a valid bcrypt hash for "Admin@123".
-- If you need to regenerate, run: java com.mrboard.system.utils.PasswordGenerator Admin@123
INSERT INTO users (username, password, email, display_name, department, password_changed)
VALUES ('admin', '$2b$12$1SoJpIKAddiHEGWf8mQlK.r7mePkVN2csHGu3TsnJX9xA7aZ84gju', 'admin@mrboard.com', '系统管理员', '技术部', 1);

-- Link admin user to admin role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.code = 'admin';

-- ============================================================
-- Phase 2: Business Tables (Git Source, Project, MR, CI, Sync Log)
-- ============================================================

-- ------------------------------------------------------------
-- Table: git_sources
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS git_sources (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Git源ID',
    name            VARCHAR(64)  NOT NULL UNIQUE COMMENT '配置名称',
    platform_type   TINYINT      NOT NULL COMMENT '平台类型：1=GitLab, 2=GitHub',
    api_base_url    VARCHAR(255) NOT NULL COMMENT 'API基础地址',
    access_token    VARCHAR(512) NOT NULL COMMENT '访问令牌（AES加密）',
    webhook_secret  VARCHAR(256) COMMENT 'Webhook签名密钥',
    sync_cron       VARCHAR(32)  DEFAULT '0 */5 * * * ?' COMMENT '同步CRON表达式',
    is_active       TINYINT      DEFAULT 1 COMMENT '是否启用：1=是, 0=否',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_platform_type (platform_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git源配置表';

-- ------------------------------------------------------------
-- Table: projects
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS projects (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目ID',
    git_source_id       BIGINT       NOT NULL COMMENT '所属Git源ID',
    platform_project_id VARCHAR(64)  NOT NULL COMMENT '平台项目ID',
    name                VARCHAR(128) NOT NULL COMMENT '项目名称',
    path                VARCHAR(255) NOT NULL COMMENT '项目路径（如 group/project-name）',
    web_url             VARCHAR(255) COMMENT '项目主页URL',
    is_active           TINYINT      DEFAULT 1 COMMENT '是否同步：1=是, 0=否',
    last_sync_at        DATETIME COMMENT '上次同步时间',
    mr_count            INT          DEFAULT 0 COMMENT 'MR数量',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_source_project (git_source_id, platform_project_id),
    INDEX idx_git_source_id (git_source_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目/仓库表';

-- ------------------------------------------------------------
-- Table: mrs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mrs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MRID',
    project_id      BIGINT       NOT NULL COMMENT '所属项目ID',
    platform_mr_id  BIGINT       NOT NULL COMMENT '平台MR编号',
    title           VARCHAR(512) NOT NULL COMMENT '标题',
    description     TEXT COMMENT '描述',
    author_id       BIGINT COMMENT '提交人用户ID',
    author_name     VARCHAR(64)  NOT NULL COMMENT '提交人平台用户名',
    author_avatar   VARCHAR(255) COMMENT '提交人头像URL',
    assignee_id     BIGINT COMMENT '指派人用户ID',
    assignee_name   VARCHAR(64) COMMENT '指派人平台用户名',
    source_branch   VARCHAR(128) NOT NULL COMMENT '源分支',
    target_branch   VARCHAR(128) NOT NULL COMMENT '目标分支',
    platform_status VARCHAR(32)      NOT NULL COMMENT '平台状态：opened/merged/closed',
    board_status    VARCHAR(32)      NOT NULL COMMENT '看板状态：pending_review/reviewing/ci_checking/conflict/ready/merged/closed',
    ci_status       VARCHAR(32)      DEFAULT 'unknown' COMMENT 'CI状态：unknown/running/success/failed/canceled',
    has_conflict    TINYINT          DEFAULT 0 COMMENT '是否有冲突：0=否, 1=是',
    mergeable       TINYINT          DEFAULT 0 COMMENT '是否可合并：0=否, 1=是',
    changes_count   INT          DEFAULT 0 COMMENT '变更文件数',
    additions       INT          DEFAULT 0 COMMENT '新增行数',
    deletions       INT          DEFAULT 0 COMMENT '删除行数',
    comments_count  INT          DEFAULT 0 COMMENT '评论数',
    labels          VARCHAR(512) COMMENT '标签（JSON或逗号分隔）',
    reviewers       VARCHAR(512) COMMENT '评审人列表（逗号分隔）',
    approval_status VARCHAR(32) DEFAULT 'pending' COMMENT '评审状态：pending/reviewing/approved',
    web_url         VARCHAR(255) NOT NULL COMMENT 'MR页面链接',
    created_at      DATETIME     NOT NULL COMMENT '平台创建时间',
    updated_at      DATETIME     NOT NULL COMMENT '平台更新时间',
    merged_at       DATETIME COMMENT '合并时间',
    closed_at       DATETIME COMMENT '关闭时间',
    last_sync_at    DATETIME COMMENT '上次同步时间',
    local_updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地记录更新时间',
    UNIQUE KEY uk_project_mr (project_id, platform_mr_id),
    INDEX idx_project_status (project_id, platform_status, board_status),
    INDEX idx_author_created (author_id, created_at),
    INDEX idx_target_branch (target_branch, board_status),
    INDEX idx_board_status (board_status),
    INDEX idx_ci_status (ci_status),
    INDEX idx_has_conflict (has_conflict),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合并请求主表';

-- ------------------------------------------------------------
-- Table: mr_reviewers
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mr_reviewers (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    mr_id            BIGINT      NOT NULL COMMENT 'MRID',
    user_id          BIGINT COMMENT '本地用户ID',
    platform_username VARCHAR(64) NOT NULL COMMENT '平台用户名',
    INDEX idx_mr_id (mr_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MR评审人关联表';

-- ------------------------------------------------------------
-- Table: ci_jobs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ci_jobs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'CI任务ID',
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    platform_mr_id  BIGINT       NOT NULL COMMENT '平台MR编号',
    platform_job_id VARCHAR(64)  NOT NULL COMMENT '平台JobID',
    name            VARCHAR(128) NOT NULL COMMENT 'Job名称',
    stage           VARCHAR(64) COMMENT 'Stage名称',
    status          VARCHAR(32)  NOT NULL COMMENT '状态：running/success/failed/canceled',
    log_url         VARCHAR(255) COMMENT '日志链接',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '结束时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_platform_mr_id (platform_mr_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CI任务记录表';

-- ------------------------------------------------------------
-- Table: sync_logs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sync_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '同步日志ID',
    project_id      BIGINT   COMMENT '项目ID',
    git_source_id   BIGINT   NOT NULL COMMENT 'Git源ID',
    sync_type       VARCHAR(32)  NOT NULL COMMENT '同步类型：full/incremental',
    trigger_type    VARCHAR(32)  NOT NULL COMMENT '触发方式：cron/manual/webhook',
    status          VARCHAR(32)  NOT NULL COMMENT '状态：running/success/failed',
    mr_count        INT      DEFAULT 0 COMMENT '处理MR数',
    ci_count        INT      DEFAULT 0 COMMENT '处理CI数',
    total_count     INT      DEFAULT 0 COMMENT '处理总数',
    success_count   INT      DEFAULT 0 COMMENT '成功数',
    fail_count      INT      DEFAULT 0 COMMENT '失败数',
    error_msg       TEXT COMMENT '错误信息',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '结束时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_git_source_id (git_source_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步任务日志表';

-- ------------------------------------------------------------
-- Table: mr_status_history（结构与 db/V3.1 迁移脚本保持一致）
-- ------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MR状态变更历史表';

-- ------------------------------------------------------------
-- Table: report_daily_summary（结构与 db/V4.1 迁移脚本保持一致）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_daily_summary (
    summary_date     DATE NOT NULL COMMENT '统计日期',
    project_id       BIGINT NOT NULL COMMENT '项目ID',
    created_count    INT DEFAULT 0 COMMENT '当日新建MR数',
    merged_count     INT DEFAULT 0 COMMENT '当日合并MR数',
    closed_count     INT DEFAULT 0 COMMENT '当日关闭MR数',
    avg_merge_hours  DECIMAL(10,2) COMMENT '当日合并MR的平均耗时（小时）',
    ci_success_count INT DEFAULT 0 COMMENT '当日CI成功次数',
    ci_failed_count  INT DEFAULT 0 COMMENT '当日CI失败次数',
    conflict_count   INT DEFAULT 0 COMMENT '当日存在冲突的MR数',

    PRIMARY KEY (summary_date, project_id),
    INDEX idx_summary_date (summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MR日报统计汇总表';

-- ------------------------------------------------------------
-- Table: mr_comments（MR评论缓存）
-- ------------------------------------------------------------
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
