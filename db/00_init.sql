-- ============================================================
-- 00_init.sql  —  用 root 执行一次
-- 命令： mysql -u root -p < db/00_init.sql
-- 作用： 建库 + 建两个应用账号（写账号 / 只读账号）
-- 安全： 应用不要用 root；Web 版只挂只读账号
-- ============================================================

CREATE DATABASE IF NOT EXISTS slow_sql_lab
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 诊断工具用的账号：能建表、加索引、跑 EXPLAIN
CREATE USER IF NOT EXISTS 'sqlquery'@'localhost' IDENTIFIED BY 'Sqlquery@2026';
ALTER USER  'sqlquery'@'localhost' IDENTIFIED BY 'Sqlquery@2026';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON slow_sql_lab.* TO 'sqlquery'@'localhost';

-- Web 界面用的只读账号（可选）
CREATE USER IF NOT EXISTS 'sqlquery_ro'@'localhost' IDENTIFIED BY 'Sqlquery@2026';
ALTER USER  'sqlquery_ro'@'localhost' IDENTIFIED BY 'Sqlquery@2026';
GRANT SELECT ON slow_sql_lab.* TO 'sqlquery_ro'@'localhost';

-- 读慢日志表需要单独授权（mysql.slow_log 不在 slow_sql_lab 库里）
-- 注意：开启慢日志、改 long_query_time 需要 SYSTEM_VARIABLES_ADMIN，
--       用 root 执行，不要把这个权限给应用账号。
GRANT SELECT ON mysql.slow_log TO 'sqlquery'@'localhost';

FLUSH PRIVILEGES;

-- 验证：
--   mysql -u sqlquery -p -e "SHOW DATABASES; SELECT CURRENT_USER();"
-- 备注：密码含 @，shell 里用单引号包住即可；JDBC 里密码是独立参数，不用转义。

-- 如果建号时报 "Access denied ... GRANT"，说明当前 root 账号没有 GRANT OPTION，
-- 换一个有权限的账号，或让 DBA 执行本文件。
