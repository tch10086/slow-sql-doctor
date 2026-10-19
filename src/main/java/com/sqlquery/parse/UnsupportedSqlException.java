package com.sqlquery.parse;

/**
 * 当前版本不支持的 SQL 形态（子查询、UNION、DML 等）。
 */
public class UnsupportedSqlException extends RuntimeException {

    public UnsupportedSqlException(String message) {
        super(message);
    }

    public UnsupportedSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
