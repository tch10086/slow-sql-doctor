package com.sqlquery.collect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
