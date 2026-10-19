package com.sqlquery.bench;

import com.sqlquery.collect.SessionStats;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单的基准测量：预热 + N 次取中位数，同时记录 Handler_read 差值。
 */
public final class Benchmark {

    private Benchmark() {
    }

    public static Measurement measure(Connection conn, String sql) throws SQLException {
        return measure(conn, sql, 2, 3);
    }

    public static Measurement measure(Connection conn, String sql, int warmups, int runs) throws SQLException {
        for (int i = 0; i < warmups; i++) {
            executeOnce(conn, sql);
        }

        List<Long> times = new ArrayList<>();
        List<Long> handlers = new ArrayList<>();
        long rows = 0;
        for (int i = 0; i < runs; i++) {
            long before = SessionStats.handlerReads(conn);
            long start = System.nanoTime();
            rows = executeOnce(conn, sql);
            times.add((System.nanoTime() - start) / 1_000_000);
            handlers.add(SessionStats.handlerReads(conn) - before);
        }

        return new Measurement(median(times), median(handlers), rows);
    }

    private static long executeOnce(Connection conn, String sql) throws SQLException {
        long rows = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(60);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
