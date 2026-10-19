-- ============================================================
-- 01_schema.sql  —  表结构与初始索引
-- 命令： mysql -u sqlquery -p slow_sql_lab < db/01_schema.sql
-- 设计要点：
--   t_order.user_id 有索引、create_time 无索引、status 低区分度
--   t_order.order_no 有索引，用来演示"字符串列被数字比较 → 索引失效"
--   t_user.username 有索引，用来演示 LIKE '%x' 失效
--   t_user.city 有索引，用来演示 JOIN 中"被驱动表"的索引
-- ============================================================

-- 两张表都建在 slow_sql_lab 库里。
-- 如果报 "Unknown database 'slow_sql_lab'"，先执行 db/00_init.sql 建库。
USE slow_sql_lab;

DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username    VARCHAR(32)     NOT NULL,
  city        VARCHAR(32)     NOT NULL,
  age         TINYINT UNSIGNED NOT NULL,
  created_at  DATETIME        NOT NULL,
  PRIMARY KEY (id),
  KEY idx_city (city),
  KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_order (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  order_no    VARCHAR(32)     NOT NULL,
  amount      DECIMAL(10,2)   NOT NULL,
  status      TINYINT         NOT NULL COMMENT '0-5，低区分度',
  remark      VARCHAR(255)    DEFAULT NULL,
  create_time DATETIME        NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 造数小技巧：先删二级索引，插完再建，速度差 2~3 倍
-- 造数前执行：
--   ALTER TABLE t_order DROP INDEX idx_user_id, DROP INDEX idx_order_no;
-- 造数后执行：
--   ALTER TABLE t_order ADD INDEX idx_user_id (user_id), ADD INDEX idx_order_no (order_no);
-- 为什么要这样？写索引页 = 随机 I/O + 页分裂，100 万行时非常明显。
-- ------------------------------------------------------------

-- 造数完成后的验收 SQL：
--   SELECT COUNT(*) FROM t_user;                         -- 100000
--   SELECT COUNT(*) FROM t_order;                        -- 1000000
--   SELECT COUNT(DISTINCT user_id) / COUNT(*) FROM t_order;  -- ≈ 0.1（10 万用户里 2 万下了 80% 的单）
--   SELECT user_id, COUNT(*) c FROM t_order GROUP BY user_id ORDER BY c DESC LIMIT 5;
