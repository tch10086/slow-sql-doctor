-- ============================================================
-- 03_optimized_indexes.sql  —  可选：复现"优化后"效果
--
-- 注意：执行后 baseline 会变化。先复制 docs/baseline.csv 再执行。
-- 回滚见文件末尾。
-- ============================================================

USE slow_sql_lab;

-- Q08 / Q02：排序与范围
ALTER TABLE t_order ADD INDEX idx_create_time (create_time);

-- Q01：等值 + 排序，消除 filesort
ALTER TABLE t_order ADD INDEX idx_user_time (user_id, create_time);

-- Q07 / Q11：覆盖索引，减少回表
ALTER TABLE t_order ADD INDEX idx_user_amount (user_id, amount);

-- Q14：分组列（区分度低，收益有限，仅用于对比）
ALTER TABLE t_order ADD INDEX idx_status (status);

-- 回滚（需要时逐条执行）：
-- ALTER TABLE t_order DROP INDEX idx_create_time;
-- ALTER TABLE t_order DROP INDEX idx_user_time;
-- ALTER TABLE t_order DROP INDEX idx_user_amount;
-- ALTER TABLE t_order DROP INDEX idx_status;
