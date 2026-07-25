-- 阶段五：SQL 慢查询优化索引
-- 基于看板查询、报表统计、WebHook 审计等高频 SQL 添加索引

-- mrs 表常用查询索引
CREATE INDEX IF NOT EXISTS idx_mrs_project_id ON mrs(project_id);
CREATE INDEX IF NOT EXISTS idx_mrs_board_status ON mrs(board_status);
CREATE INDEX IF NOT EXISTS idx_mrs_author_name ON mrs(author_name);
CREATE INDEX IF NOT EXISTS idx_mrs_target_branch ON mrs(target_branch);
CREATE INDEX IF NOT EXISTS idx_mrs_created_at ON mrs(created_at);
CREATE INDEX IF NOT EXISTS idx_mrs_merged_at ON mrs(merged_at);
CREATE INDEX IF NOT EXISTS idx_mrs_closed_at ON mrs(closed_at);
CREATE INDEX IF NOT EXISTS idx_mrs_updated_at ON mrs(updated_at);
CREATE INDEX IF NOT EXISTS idx_mrs_has_conflict ON mrs(has_conflict);
CREATE INDEX IF NOT EXISTS idx_mrs_project_status ON mrs(project_id, board_status);

-- ci_jobs 表索引
CREATE INDEX IF NOT EXISTS idx_ci_jobs_project_id ON ci_jobs(project_id);
CREATE INDEX IF NOT EXISTS idx_ci_jobs_platform_mr_id ON ci_jobs(platform_mr_id);
CREATE INDEX IF NOT EXISTS idx_ci_jobs_started_at ON ci_jobs(started_at);
CREATE INDEX IF NOT EXISTS idx_ci_jobs_status ON ci_jobs(status);

-- mr_status_history 表索引
CREATE INDEX IF NOT EXISTS idx_status_history_mr_id ON mr_status_history(mr_id);
CREATE INDEX IF NOT EXISTS idx_status_history_created_at ON mr_status_history(created_at);

-- webhook_event_logs 表索引
CREATE INDEX IF NOT EXISTS idx_webhook_created_at ON webhook_event_logs(created_at);
