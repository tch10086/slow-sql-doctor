# 慢 SQL 诊断报告

- 生成时间：2026-10-19T20:27:29.673014
- 数据库：slow_sql_lab（MySQL 8.0）

## 1. SQL

```sql
SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20
```

## 2. 执行计划

| 表 | type | key | rows(估算) | filtered | 备注 |
|---|---|---|---:|---:|---|
| t_order | ref | idx_user_id | 40 | 100.0 | [id, user_id, order_no, amount, status, remark, create_time] |

> 额外开销：`Using filesort` 

## 3. 问题

| # | 规则 | 严重度 | 证据 | 建议 |
|---|---|---|---|---|
| 1 | R07 回表 | MEDIUM | t_order: SELECT * 需要回表取整行 | 考虑覆盖索引：把 SELECT 的列补进索引，减少回表 |
| 2 | R08 文件排序或临时表 | MEDIUM | Using filesort | 调整联合索引列序（等值 → 范围 → 排序），或减少排序/分组的数据量 |

## 4. 索引建议

### 1) t_order (user_id, create_time)

```sql
ALTER TABLE `t_order` ADD INDEX `idx_user_id_create_time` (`user_id`, `create_time`);
```

- 理由：等值列 [user_id]；排序列 [create_time]
- 风险：写入时多维护一棵索引，存在写放大；现有索引 [idx_user_id] 可能成为冗余前缀，评估后再决定是否删除。

## 5. 实测

| 指标 | 值 |
|---|---:|
| 中位耗时 | 0 ms |
| Handler_reads | 41 |
| 返回行数 | 20 |

## 6. 复现

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.Main \
  -Dexec.args="SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20"
```

## 7. 已知局限

- 只支持单条 `SELECT` / `PlainSelect`，子查询和 UNION 未覆盖。
- `rows` 是估算值，报告同时给出 `Handler_reads` 实测。
- 索引建议只生成 DDL，不自动执行。
