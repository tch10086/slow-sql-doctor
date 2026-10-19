package com.sqlquery.db;

import com.sqlquery.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP 连接池。整个进程一个实例，用 try-with-resources 关闭。
 */
public final class Db implements AutoCloseable {

    private final HikariDataSource dataSource;

    public Db(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.url());
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(5_000);
        hikari.setValidationTimeout(3_000);
        hikari.setPoolName("slow-sql-doctor");
        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
