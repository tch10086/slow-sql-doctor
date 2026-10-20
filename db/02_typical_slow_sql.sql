-- ============================================================
-- 16 条典型慢 SQL
-- 这是查询清单，不是一键脚本：一次只执行一条。
-- 批量采集基线用 BaselineRunner；Q12 是笛卡尔积，只 EXPLAIN。
-- ============================================================

USE slow_sql_lab;

-- Q01：user_id 有索引，但 create_time 不在索引里，排完还要 filesort。
-- 想一次拿到有序结果，可以试 (user_id, create_time)。
SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20;

-- Q02：DATE() 把列包住了，create_time 的索引用不上。
-- 改成 create_time >= '2026-03-01' AND create_time < '2026-03-02' 才能走范围。
SELECT * FROM t_order WHERE DATE(create_time) = '2026-03-01';

-- Q03：order_no 是 varchar，拿数字去比会先转列，idx_order_no 直接废掉。
SELECT * FROM t_order WHERE order_no = 202610180001;

-- Q04：% 开头，B+ 树不知道从哪开始找，只能全扫。
SELECT * FROM t_user WHERE username LIKE '%张%';

-- Q05：OR 两边只有 user_id 有索引，status 没有，整体退化成全表扫。
SELECT * FROM t_order WHERE user_id = 10086 OR status = 3;

-- Q06：status 只有 6 个值，单独建索引意义不大，全扫反而是合理的。
SELECT * FROM t_order WHERE status = 3;

-- Q07：idx_user_id 里没有 amount，查 amount 得回表。
-- 覆盖索引 (user_id, amount) 可以省掉这一步。
SELECT id, user_id, amount FROM t_order WHERE user_id = 10086;

-- Q08：按无索引列排序，全表扫 + filesort。
SELECT * FROM t_order ORDER BY create_time DESC LIMIT 10;

-- Q09：深分页，前 95 万行扫完就扔，只留 20 行。
SELECT * FROM t_order LIMIT 950000, 20;

-- Q10：COUNT + 低区分度条件，索引也救不了，考虑汇总表。
SELECT COUNT(*) FROM t_order WHERE status = 1;

-- Q11：t_user 走 idx_city，t_order 走 idx_user_id；t_order 取 amount 会回表。
SELECT o.id, o.amount
FROM t_order o
JOIN t_user u ON o.user_id = u.id
WHERE u.city = '杭州';

-- Q12：没有连接条件的笛卡尔积，1,000,000 × 100,000。
-- 只在有数据的库上 EXPLAIN，别真跑。
SELECT * FROM t_order, t_user WHERE t_order.status = 1 AND t_user.age > 18;

-- Q13：等值在前、范围在后，(user_id, create_time) 正好。
SELECT * FROM t_order
WHERE user_id IN (10086, 10087, 10088)
  AND create_time >= '2026-01-01';

-- Q14：GROUP BY 没索引，走临时表；加 (status) 能消掉临时表，但收益有限。
SELECT status, COUNT(*) FROM t_order GROUP BY status;

-- Q15：范围列后面的 create_time 没法用于排序，还是会 filesort。
SELECT * FROM t_order WHERE amount > 5000 ORDER BY create_time LIMIT 20;

-- Q16：SUBSTRING 把 order_no 包住了，idx_order_no 用不上。
SELECT * FROM t_order WHERE SUBSTRING(order_no, 1, 3) = 'ORD';
