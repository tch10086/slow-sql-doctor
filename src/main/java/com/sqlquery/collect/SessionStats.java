package com.sqlquery.collect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 会话级 Handler_read_* 计数。
 * 执行 SQL 前后各取一次，差值近似"实际读了多少行/索引项"。
 */
public final class SessionStats {

    private SessionStats() {
    }

    public static long handlerReads(Connection conn) throws SQLException {
        long sum = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Handler_read%'")) {
            while (rs.next()) {
                sum += rs.getLong(2);
            }
        }
        return sum;
    }
}
