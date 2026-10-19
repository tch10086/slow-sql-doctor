-- ============================================================
-- 02_typical_slow_sql.sql  —  16 条典型慢 SQL（Q01-Q15 主集，Q16 加分）
--
-- 说明：这是"查询清单"，不是一键执行脚本。
--   - 一键采集基线请运行 BaselineRunner（见手册 §3.6）；
--   - 手工验证时，一次只选中一条执行；
--   - 千万不要 source 整个文件：Q12 是笛卡尔积，会在 100 万行上爆炸。
-- ============================================================

-- 表在 slow_sql_lab 库里；DataGrip 里请把默认库设为 slow_sql_lab，
-- 或先执行 USE slow_sql_lab; 再执行单条 SQL。
USE slow_sql_lab;

-- Q01 已有 user_id 索引，但 ORDER BY create_time 需要额外排序
--     预期：type=ref, key=idx_user_id, Extra=Using filesort
--     建议：(user_id, create_time)  —— 等值 + 排序，一次索引扫描拿到有序结果
SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20;

-- Q02 WHERE 列被函数包裹 → 索引（即使有）失效
--     预期：type=ALL, rows≈100万
--     建议：改成范围条件 create_time >= '2026-10-01' AND create_time < '2026-10-02'
SELECT * FROM t_order WHERE DATE(create_time) = '2026-10-01';

-- Q03 字符串列被数字比较 → 隐式类型转换 → 索引失效
--     预期：type=ALL，key=NULL（虽然 idx_order_no 存在）
--     建议：WHERE order_no = '202610180001'
SELECT * FROM t_order WHERE order_no = 202610180001;

-- Q04 前导通配符 → 索引失效
--     预期：type=ALL，key=NULL（虽然 idx_username 存在）
--     建议：后缀匹配 / 全文索引 / 倒排字段
SELECT * FROM t_user WHERE username LIKE '%张%';

-- Q05 OR 两侧只有一侧有索引 → 整体可能退化为全表扫描
--     预期：type=ALL（status 无索引），或 index_merge（视版本/成本）
--     建议：拆 UNION ALL，或给 status 补索引后再评估
SELECT * FROM t_order WHERE user_id = 10086 OR status = 3;

-- Q06 低区分度列单独查询 → 全表扫描是合理的，不要盲目建索引
--     预期：type=ALL，rows≈100万；选择性 ≈ 1/6
--     建议：不建单列索引；考虑 (status, create_time) 或汇总表
SELECT * FROM t_order WHERE status = 3;

-- Q07 回表：SELECT 的 amount 不在 idx_user_id 中
--     预期：type=ref, Extra 无 Using index → 需要回表
--     建议：覆盖索引 (user_id, amount)
SELECT id, user_id, amount FROM t_order WHERE user_id = 10086;

-- Q08 无索引列排序 → 全表扫描 + 文件排序
--     预期：type=ALL, Extra=Using filesort
--     建议：create_time 单列索引，或业务侧限制时间范围
SELECT * FROM t_order ORDER BY create_time DESC LIMIT 10;

-- Q09 深分页：扫描 95 万行只返回 20 行
--     预期：type=ALL, rows≈100万
--     建议：延迟关联 / 游标分页 WHERE id > last_id
SELECT * FROM t_order LIMIT 950000, 20;

-- Q10 COUNT + 低区分度条件 → 全表扫描；索引不一定能救
--     预期：type=ALL（或 index，若建了 status 索引）
--     建议：汇总表 / 近似统计 / 业务上接受缓存
SELECT COUNT(*) FROM t_order WHERE status = 1;

-- Q11 JOIN：t_user 走 idx_city，t_order 走 idx_user_id
--     预期：t_user type=ref；t_order type=ref（被驱动表）
--     建议：确认驱动表选择；给 t_order 加 (user_id, amount) 覆盖
SELECT o.id, o.amount
FROM t_order o
JOIN t_user u ON o.user_id = u.id
WHERE u.city = '杭州';

-- Q12 隐式笛卡尔积：没有连接条件
--     预期：两表全扫描 + 结果集爆炸（不要在有数据的库上真跑）
--     建议：补 ON 条件
SELECT * FROM t_order, t_user WHERE t_order.status = 1 AND t_user.age > 18;

-- Q13 等值 + 范围：索引列顺序决定能否用上范围
--     预期：type=range（idx_user_id），create_time 只能做过滤，不能进索引
--     建议：(user_id, create_time)
SELECT * FROM t_order
WHERE user_id IN (10086, 10087, 10088)
  AND create_time >= '2026-01-01';

-- Q14 GROUP BY 无索引 → 临时表 + 排序
--     预期：type=ALL, Extra=Using temporary; Using filesort
--     建议：(status) 单列索引可消除临时表，但低区分度收益有限 → 权衡
SELECT status, COUNT(*) FROM t_order GROUP BY status;

-- Q15 范围条件 + 排序：范围列之后的索引列无法用于排序
--     预期：type=ALL, Extra=Using filesort
--     建议：(amount, create_time) 也救不了排序；改成时间范围过滤或业务分页
SELECT * FROM t_order WHERE amount > 5000 ORDER BY create_time LIMIT 20;

-- Q16（加分）函数包裹 + 字符串索引
--     预期：type=ALL, key=NULL（idx_order_no 存在）
--     建议：改成 order_no LIKE 'ORD%'，或建函数索引（8.0.13+）
SELECT * FROM t_order WHERE SUBSTRING(order_no, 1, 3) = 'ORD';
