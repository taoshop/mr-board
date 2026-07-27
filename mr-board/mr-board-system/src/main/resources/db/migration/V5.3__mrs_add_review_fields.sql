-- mrs 表增加 review 相关字段
ALTER TABLE mrs
    ADD COLUMN reviewers VARCHAR(512) DEFAULT NULL COMMENT '评审人列表（逗号分隔）',
    ADD COLUMN approval_status VARCHAR(32) DEFAULT 'pending' COMMENT '评审状态：pending/reviewing/approved';

-- 旧状态数据迁移（v2 方案：CI 不再作为独立列）
UPDATE mrs SET board_status = 'pending_review' WHERE board_status = 'open';
UPDATE mrs SET board_status = 'pending_review' WHERE board_status = 'testing';
UPDATE mrs SET board_status = 'conflict' WHERE board_status = 'failed';
