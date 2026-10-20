package com.sqlquery.collect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sqlquery.model.ExplainPlan;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

// 只放行 SELECT。EXPLAIN 不会执行原 SQL，后面 SHOW WARNINGS 拿优化器改写后的语句。
public final class ExplainCollector {

    public ExplainPlan collect(Connection conn, String sql) throws SQLException {
        requireSelect(sql);
        String json = explainJson(conn, sql);

        ExplainPlan plan;
        try {
            plan = ExplainParser.parse(json);
        } catch (JsonProcessingException e) {
            throw new SQLException("cannot parse EXPLAIN JSON", e);
        }

        plan.setRawJson(json);
        plan.setRewrittenSql(readRewrittenSql(conn));
        return plan;
    }

    
    public String explainJson(Connection conn, String sql) throws SQLException {
        requireSelect(sql);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN FORMAT=JSON " + sql)) {
            if (!rs.next()) {
                throw new SQLException("EXPLAIN returned no rows");
            }
            return rs.getString(1);
        }
    }

    private static void requireSelect(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is empty");
        }
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT is supported, got: "
                    + normalized.substring(0, Math.min(20, normalized.length())));
        }
    }

    
    private static String readRewrittenSql(Connection conn) throws SQLException {
        String rewritten = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW WARNINGS")) {
            while (rs.next()) {
                if (rs.getInt("Code") == 1003) {
                    rewritten = rs.getString("Message");
                }
            }
        }
        return rewritten;
    }
}
