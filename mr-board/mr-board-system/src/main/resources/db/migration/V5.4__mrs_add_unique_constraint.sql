-- mrs 表增加 (project_id, platform_mr_id) 唯一约束
-- 用于防止并发同步插入重复记录，导致 selectOne 报错

-- 1. 先清除重复记录：对每个 (project_id, platform_mr_id) 只保留 id 最大的一条
DELETE FROM mrs WHERE id NOT IN (
  SELECT keep_id FROM (
    SELECT MAX(m2.id) AS keep_id FROM mrs m2 GROUP BY m2.project_id, m2.platform_mr_id
  ) AS t
);

-- 2. 添加唯一约束
ALTER TABLE mrs
    ADD UNIQUE KEY uk_mrs_project_mr (project_id, platform_mr_id);
