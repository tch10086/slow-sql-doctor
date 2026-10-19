package com.sqlquery;

import com.sqlquery.collect.ExplainCollector;
import com.sqlquery.collect.SessionStats;
import com.sqlquery.config.AppConfig;
import com.sqlquery.db.Db;
import com.sqlquery.model.ExplainPlan;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * D3/D4 演示入口：
 *   1) 连接数据库
 *   2) 采集 EXPLAIN FORMAT=JSON 并解析
 *   3) 实际执行一次，打印耗时和 Handler_read 差值
 *
 * 运行（项目根目录）：
 *   mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.Main
 * 或传入 SQL：
 *   mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.Main -Dexec.args="SELECT ..."
 */
public final class Main {

    private static final String DEFAULT_SQL =
            "SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20";

    public static void main(String[] args) throws Exception {
        String sql = args.length == 0 ? DEFAULT_SQL : String.join(" ", args);

        AppConfig config = AppConfig.load();
        try (Db db = new Db(config);
             Connection conn = db.getConnection()) {

            ExplainCollector collector = new ExplainCollector();
            ExplainPlan plan = collector.collect(conn, sql);

            System.out.println("SQL: " + sql);
            System.out.println();
            System.out.println("--- EXPLAIN JSON ---");
            System.out.println(plan.getRawJson());
            System.out.println("--- PLAN ---");
            System.out.println(plan.describe());
            if (plan.getRewrittenSql() != null) {
                System.out.println("--- REWRITTEN ---");
                System.out.println(plan.getRewrittenSql());
            }

            long before = SessionStats.handlerReads(conn);
            long start = System.nanoTime();
            long rows = 0;
            try (Statement st = conn.createStatement()) {
                st.setQueryTimeout(60);
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        rows++;
                    }
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            long handlerReads = SessionStats.handlerReads(conn) - before;

            System.out.println();
            System.out.printf("execute: rows=%d, time=%d ms, handler_reads=%d%n",
                    rows, elapsedMs, handlerReads);
        }
    }
}
