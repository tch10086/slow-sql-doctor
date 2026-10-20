package com.sqlquery.tools;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Random;

// 造数用：80% 订单压到 2 万热用户上，制造倾斜；建索引前先插数据会快很多。
public class SeedData {
    static final String URL = "jdbc:mysql://localhost:3306/slow_sql_lab"
            + "?rewriteBatchedStatements=true"
            + "&connectionTimeZone=Asia/Shanghai"
            + "&characterEncoding=utf8&useSSL=false";
    static final String USER = "sqlquery";
    static final String PASS = "Sqlquery@2026";

    static final String[] CITIES = {"杭州","上海","北京","深圳","成都","武汉","南京","广州","西安","苏州"};
    static final LocalDateTime BASE = LocalDateTime.of(2025, 10, 1, 0, 0);

    public static void main(String[] args) throws Exception {
        Random r = new Random(42);
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            c.setAutoCommit(false);
            seedUsers(c, r, 100_000);
            seedOrders(c, r, 1_000_000);
            c.commit();
        }
    }

    static void seedUsers(Connection c, Random r, int n) throws SQLException {
        String sql = "INSERT INTO t_user(username, city, age, created_at) VALUES (?,?,?,?)";
        long t0 = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                ps.setString(1, (i % 3 == 0) ? "张" + i : "user" + i);
                ps.setString(2, CITIES[r.nextInt(CITIES.length)]);
                ps.setInt(3, 18 + r.nextInt(43));
                ps.setTimestamp(4, Timestamp.valueOf(BASE.plusSeconds(r.nextInt(365 * 86400))));
                ps.addBatch();
                if (i % 5_000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        System.out.println("users done: " + (System.currentTimeMillis() - t0) + " ms");
    }

    static void seedOrders(Connection c, Random r, int n) throws SQLException {
        String sql = "INSERT INTO t_order(user_id, order_no, amount, status, remark, create_time) "
                + "VALUES (?,?,?,?,?,?)";
        long t0 = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                long uid = (r.nextDouble() < 0.8)
                        ? 1 + r.nextInt(20_000)
                        : 20_001 + r.nextInt(80_000);
                ps.setLong(1, uid);
                ps.setString(2, "ORD" + (10_000_000L + i));
                ps.setBigDecimal(3, BigDecimal.valueOf(1 + r.nextInt(10_000))
                        .add(BigDecimal.valueOf(r.nextInt(100)).divide(BigDecimal.valueOf(100))));
                ps.setInt(4, r.nextInt(6));
                ps.setString(5, r.nextInt(10) == 0 ? "加急" : null);
                ps.setTimestamp(6, Timestamp.valueOf(BASE.plusSeconds(r.nextInt(365 * 86400))));
                ps.addBatch();
                if (i % 5_000 == 0) {
                    ps.executeBatch();
                    if (i % 100_000 == 0) System.out.println("orders: " + i);
                }
            }
            ps.executeBatch();
        }
        System.out.println("orders done: " + (System.currentTimeMillis() - t0) + " ms");
    }
}