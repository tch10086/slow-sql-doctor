# MySQL 慢 SQL 诊断与索引优化工具

输入一条 SQL，输出三样东西：

1. **为什么慢**：`EXPLAIN FORMAT=JSON` + AST 特征，定位全表扫描、索引失效、回表、filesort、临时表、深分页等问题；
2. **该建什么索引**：按"等值 → 范围 → 排序 → 覆盖"生成 `ALTER TABLE`，并与现有索引比对；
3. **优化前后差多少**：同连接、预热 2 次、正式 3 次取中位数，同时记录 `Handler_read_*` 差值。

> 个人项目，Java 17 + MySQL 8.0。目标不是替代 DBA，而是把"看执行计划 → 归因 → 建议 → 验证"做成可复现的闭环。

---

## 功能

- 解析单条 `SELECT` 的 AST，抽取等值列、范围列、JOIN 列、排序列、分组列、函数包裹列、前导通配、隐式转换候选；
- 采集 `EXPLAIN FORMAT=JSON`，递归处理 `nested_loop` / `ordering_operation` / `grouping_operation`；
- 10 条规则输出问题（带证据和建议）；
- 索引建议：列序、覆盖索引、与 `information_schema.STATISTICS` 比对、低区分度提示；
- 生成 Markdown + HTML 报告（`docs/reports/`），示例见 [`sample-q01.html`](docs/reports/sample-q01.html)；
- 慢日志批量聚合：归一化、指纹、按总耗时排序；
- 31 个 JUnit 测试，JSON fixture 不依赖数据库。

---

## 架构

```
SQL 文本
   │
   ├─► SqlAnalyzer (JSqlParser 5.3) ──► SqlFeatures（等值/范围/JOIN/排序/函数/通配）
   │
   ├─► ExplainCollector ──► EXPLAIN FORMAT=JSON + SHOW WARNINGS
   │        └─► ExplainParser (Jackson 递归) ──► ExplainPlan / TableAccess
   │
   ├─► TableMetaLoader ──► information_schema（列类型、索引、Cardinality）
   │
   ├─► RuleEngine ──► 10 条规则 ──► Issue 列表
   │
   ├─► IndexAdvisor ──► IndexSuggestion（DDL + 理由 + 风险）
   │
   └─► ReportGenerator ──► Markdown / HTML
```

包结构：

```
com.sqlquery
├─ parse/      SqlAnalyzer, UnsupportedSqlException
├─ collect/    ExplainCollector, ExplainParser, SessionStats
├─ meta/       TableMetaLoader, TableMeta, IndexMeta, ColumnMeta
├─ rule/       RuleEngine, Rule, Issue, Severity, rules/*
├─ advise/     IndexAdvisor, IndexSuggestion
├─ bench/      Benchmark, Measurement
├─ report/     ReportGenerator, AnalysisResult
├─ slowlog/    SlowLogParser, SlowLogAggregator
├─ service/    DiagnosisService
└─ tools/      SeedData, BaselineRunner
```

---

## 快速开始

### 1. 建库和账号

```bash
mysql -u root -p < db/00_init.sql
```

PowerShell 不支持 `<`，可以登录后执行：

```sql
source db/00_init.sql;
```

### 2. 建表

```bash
mysql -u sqlquery -p slow_sql_lab < db/01_schema.sql
```

两张表：`t_user` 10 万行、`t_order` 100 万行（`user_id` 有索引、`create_time` 无索引、`status` 低区分度）。

### 3. 造数（可选，仓库不提交数据）

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.tools.SeedData
```

### 4. 构建并运行

```bash
mvn -q -DskipTests package
java -jar target/slow-sql-doctor-1.0.0.jar
```

默认诊断 Q01。也可以传 SQL：

```bash
java -jar target/slow-sql-doctor-1.0.0.jar "SELECT * FROM t_order WHERE status = 3"
```

`-v` 额外打印 EXPLAIN JSON：

```bash
java -jar target/slow-sql-doctor-1.0.0.jar -v "SELECT ..."
```

只测耗时：

```bash
java -jar target/slow-sql-doctor-1.0.0.jar bench "SELECT ..."
```

慢日志聚合：

```bash
java -jar target/slow-sql-doctor-1.0.0.jar slowlog /path/to/slow.log
```

Windows 控制台中文乱码时，先执行 `chcp 65001`，或在 Windows Terminal 里运行。

---

## 10 条规则

| ID | 问题 | 判定依据 | 建议 |
|---|---|---|---|
| R01 | 全表扫描 | `type=ALL` 且 `rows ≥ 1 万` | 建索引 / 缩小范围 |
| R02 | 索引未命中 | `possible_keys` 非空但 `key=NULL` | 查最左前缀、转换、通配 |
| R03 | 函数导致失效 | WHERE 列被函数包裹 | 改写条件或函数索引 |
| R04 | 隐式类型转换 | 字符串列 vs 数字字面量 | 字面量加引号 / 统一类型 |
| R05 | 前导通配 | `LIKE '%x'` | 后缀匹配 / 全文索引 |
| R06 | OR 失效 | 含 OR 且未走索引 | 拆 UNION ALL / 补索引 |
| R07 | 回表 | 二级索引不覆盖 SELECT 列 | 覆盖索引 |
| R08 | 文件排序/临时表 | `Using filesort` / `Using temporary` | 调整索引列序 |
| R09 | 深分页 | `LIMIT offset` 且 offset ≥ 1 万 | 延迟关联 / 游标分页 |
| R10 | 隐式笛卡尔积 | JOIN 无 ON | 补连接条件 |

---

## 索引建议算法

1. 收集列并分桶：`JOIN` 列、等值列、范围列、排序列/分组列；
2. 等值列按选择性（`Cardinality / TABLE_ROWS`）降序；
3. 范围列最多 1 个，放在等值列之后；
4. 有范围列时不再追加排序列（索引顺序已被范围打断）；
5. SELECT 列可覆盖时追加为覆盖索引（最多补 3 列）；
6. 与现有索引比对：候选是现有索引前缀 → 跳过；现有索引是候选前缀 → 提示可能冗余；
7. 输出 DDL + 理由 + 风险，**不自动执行**。

---

## 实测效果

数据：`t_order` 100 万行、`t_user` 10 万行；热缓存、单连接、3 次中位数。完整数据在 [`docs/baseline.csv`](docs/baseline.csv) 和 [`docs/compare.csv`](docs/compare.csv)。

| 场景 | 优化前 | 优化后 | 手段 |
|---|---:|---:|---|
| Q08 排序 + 全表扫描 | 254 ms / 1,000,003 Handler | 0 ms / 11 | 加 `create_time` 索引 |
| Q02 `DATE(create_time)` | 232 ms / 1,000,003 | 8 ms / 2,726 | 改写为范围 + 索引 |
| Q03 `order_no = 数字` | 395 ms / 1,000,003 | 0 ms / 2 | 字面量加引号，走已有索引 |
| Q01 等值 + 排序 | 0 ms / 41 | 0 ms / 20 | `(user_id, create_time)` 消除 filesort |
| Q14 `GROUP BY status` | 206 ms / 2,000,010 | 98 ms / 1,000,002 | `status` 索引消除临时表 |

> `0 ms` 表示小于 1 毫秒。绝对值换机器会变，看数量级和 Handler 差异。

---

## 测试

```bash
mvn test
```

```text
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

覆盖：

- `ExplainParserTest`：单表 / JOIN / 分组临时表 / const / 缺字段；
- `SqlAnalyzerTest`：等值、范围、函数、隐式转换、JOIN、笛卡尔积、深分页；
- `RuleEngineTest`：10 条规则的代表场景；
- `IndexAdvisorTest`：列序、覆盖、已有索引跳过；
- `ReportGeneratorTest`：Markdown/HTML 内容与转义；
- `SlowLogParserTest`：日志解析、归一化、聚合。

---

## 已知局限

- 只支持单条 `SELECT` / `PlainSelect`；子查询、UNION、CTE、窗口函数未覆盖，解析失败会抛 `UnsupportedSqlException`。
- EXPLAIN 的 `rows` 是估算值，报告同时给 `Handler_read_*` 实测。
- 索引建议基于规则，不保证优化器一定采用；DDL 需要人工确认。
- 选择性优先用 `STATISTICS.CARDINALITY`，没有统计信息的列会退化为经验值。
- 慢日志解析按分号切分，字符串/注释里带分号会误判。
- 测量是热缓存、单连接、无并发；生产环境要重新测。

---

## 设计取舍

- **为什么用 JSqlParser，而不是正则**：函数包裹、OR、别名、子查询这些结构正则不可靠；AST 才能稳定判断。
- **为什么用 `EXPLAIN FORMAT=JSON`**：JOIN 时有 `nested_loop` 层级，表格 EXPLAIN 丢失结构。
- **为什么同时看 `Handler_read_*`**：`rows` 是估算，实测计数才能证明"扫描行数下降"。
- **为什么规则引擎用接口 + 独立类**：每条规则可单测、可开关、可解释；单条规则异常不影响整体。
- **为什么只生成 DDL 不执行**：DDL 会锁元数据、影响写入，必须人工确认。

---

## 许可

个人学习项目，MIT License。
