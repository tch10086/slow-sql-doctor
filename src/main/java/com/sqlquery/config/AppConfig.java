package com.sqlquery.config;

/**
 * 数据库连接配置。
 *
 * 默认值面向本机实验库；可以用环境变量覆盖，避免把密码写死在代码里：
 *   SQLQUERY_DB_URL / SQLQUERY_DB_USER / SQLQUERY_DB_PASSWORD / SQLQUERY_DB_POOL_SIZE
 */
public record AppConfig(String url, String user, String password, int poolSize) {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/slow_sql_lab"
            + "?connectionTimeZone=Asia/Shanghai&characterEncoding=utf8&useSSL=false";

    public static AppConfig load() {
        String url = env("SQLQUERY_DB_URL", DEFAULT_URL);
        String user = env("SQLQUERY_DB_USER", "sqlquery");
        String password = env("SQLQUERY_DB_PASSWORD", "Sqlquery@2026");
        int poolSize = Integer.parseInt(env("SQLQUERY_DB_POOL_SIZE", "4"));
        return new AppConfig(url, user, password, poolSize);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public String toString() {
        return "AppConfig[url=" + url + ", user=" + user + ", poolSize=" + poolSize + "]";
    }
}
