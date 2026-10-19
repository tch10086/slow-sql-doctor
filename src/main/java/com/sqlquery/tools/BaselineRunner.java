package com.sqlquery.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * D2 基线采集器。
 *
 * 对 16 条典型慢 SQL 做：
 *   1) EXPLAIN：记录 type / key / rows / Extra
 *   2) 预热 2 次 + 正式 3 次：取中位耗时
 *   3) 每次记录 Handler_read_* 差值：近似"实际扫描行数"
 *   4) 输出 docs/baseline.csv
 *
 * 特殊处理：
 *   Q12 是笛卡尔积，只 EXPLAIN，绝不执行。
 *
 * 运行（项目根目录）：
 *   mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.tools.BaselineRunner
 */
public final class BaselineRunner {

    private static final String URL = "jdbc:mysql://localhost:3306/slow_sql_lab"
            + "?connectionTimeZone=Asia/Shanghai&characterEncoding=utf8&useSSL=false";
    private static final String USER = "sqlquery";
    private static final String PASS = "Sqlquery@2026";
    private static final Path CSV = Path.of("docs", "baseline.csv");

    private record Query(String id, String sql, boolean measure, String note) {}

    private record Plan(String type, String key, long rows, String extra) {}

    private record Sample(long nanos, long handlers, long rows) {}

    private static final List<Query> QUERIES = List.of(
            new Query("Q01",
                    "SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20",
                    true, ""),
            new Query("Q02",
                    "SELECT * FROM t_order WHERE DATE(create_time) = '2026-03-01'",
                    true, ""),
            new Query("Q03",
                    "SELECT * FROM t_order WHERE order_no = 202610180001",
                    true, ""),
            new Query("Q04",
                    "SELECT * FROM t_user WHERE username LIKE '%张%'",
                    true, ""),
            new Query("Q05",
                    "SELECT * FROM t_order WHERE user_id = 10086 OR status = 3",
                    true, ""),
            new Query("Q06",
                    "SELECT * FROM t_order WHERE status = 3",
                    true, ""),
            new Query("Q07",
                    "SELECT id, user_id, amount FROM t_order WHERE user_id = 10086",
                    true, ""),
            new Query("Q08",
                    "SELECT * FROM t_order ORDER BY create_time DESC LIMIT 10",
                    true, ""),
            new Query("Q09",
                    "SELECT * FROM t_order LIMIT 950000, 20",
                    true, ""),
            new Query("Q10",
                    "SELECT COUNT(*) FROM t_order WHERE status = 1",
                    true, ""),
            new Query("Q11",
                    "SELECT o.id, o.amount FROM t_order o JOIN t_user u ON o.user_id = u.id WHERE u.city = '杭州'",
                    true, ""),
            new Query("Q12",
                    "SELECT * FROM t_order, t_user WHERE t_order.status = 1 AND t_user.age > 18",
                    false, "隐式笛卡尔积：只 EXPLAIN，不执行"),
            new Query("Q13",
                    "SELECT * FROM t_order WHERE user_id IN (10086, 10087, 10088) AND create_time >= '2026-01-01'",
                    true, ""),
            new Query("Q14",
                    "SELECT status, COUNT(*) FROM t_order GROUP BY status",
                    true, ""),
            new Query("Q15",
                    "SELECT * FROM t_order WHERE amount > 5000 ORDER BY create_time LIMIT 20",
                    true, ""),
            new Query("Q16",
                    "SELECT * FROM t_order WHERE SUBSTRING(order_no, 1, 3) = 'ORD'",
                    true, "")
    );

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        List<String> lines = new ArrayList<>();
        lines.add("qid,sql,type,key,rows,extra,median_ms,handler_reads,note");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            for (Query q : QUERIES) {
                Plan plan = explain(conn, q.sql());

                Long medianMs = null;
                Long handlers = null;
                if (q.measure()) {
                    // 预热：把数据和索引页读进 buffer pool，避免第一次计时被磁盘 IO 污染
                    for (int i = 0; i < 2; i++) {
                        runOnce(conn, q.sql());
                    }
                    List<Long> times = new ArrayList<>();
                    List<Long> handlerDeltas = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        Sample s = runOnce(conn, q.sql());
                        times.add(s.nanos());
                        handlerDeltas.add(s.handlers());
                    }
                    medianMs = median(times) / 1_000_000;
                    handlers = median(handlerDeltas);
                }

                lines.add(toCsv(q, plan, medianMs, handlers));
                System.out.printf("%s | type=%-6s key=%-14s rows=%-8d extra=%-30s | %s%n",
                        q.id(), plan.type(), plan.key(), plan.rows(), plan.extra(),
                        medianMs == null
                                ? "EXPLAIN only"
                                : medianMs + " ms, handler=" + handlers);
            }
        }

        Files.write(CSV, lines, StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("written: " + CSV.toAbsolutePath());
    }

    /** 只执行 EXPLAIN，不执行原 SQL；Q12 也安全。 */
    private static Plan explain(Connection conn, String sql) throws SQLException {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        long rows = 0;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
            while (rs.next()) {
                String type = rs.getString("type");
                if (type != null) types.add(type);

                String key = rs.getString("key");
                if (key != null) keys.add(key);

                String extra = rs.getString("Extra");
                if (extra != null && !extra.isBlank()) extras.add(extra);

                rows += rs.getLong("rows");
            }
        }

        return new Plan(
                String.join("|", types),
                keys.isEmpty() ? "" : String.join("|", keys),
                rows,
                String.join("|", extras));
    }

    /** 执行一次并消费全部结果，返回耗时与 Handler_read 差值。 */
    private static Sample runOnce(Connection conn, String sql) throws SQLException {
        long before = handlerReads(conn);
        long start = System.nanoTime();
        long rowCount = 0;

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(60);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    rowCount++;
                }
            }
        }

        long nanos = System.nanoTime() - start;
        long after = handlerReads(conn);
        return new Sample(nanos, after - before, rowCount);
    }

    private static long handlerReads(Connection conn) throws SQLException {
        long sum = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Handler_read%'")) {
            while (rs.next()) {
                sum += rs.getLong(2);
            }
        }
        return sum;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static String toCsv(Query q, Plan p, Long medianMs, Long handlers) {
        List<String> fields = Arrays.asList(
                q.id(),
                q.sql(),
                p.type(),
                p.key(),
                String.valueOf(p.rows()),
                p.extra(),
                medianMs == null ? "" : String.valueOf(medianMs),
                handlers == null ? "" : String.valueOf(handlers),
                q.note());
        return fields.stream().map(BaselineRunner::escape).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        String v = value == null ? "" : value;
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
