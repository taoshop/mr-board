-- report_daily_summary 表 DDL
-- 用于存储每日 MR 统计数据，减少实时聚合压力

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
