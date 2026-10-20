package com.sqlquery.parse;

public class UnsupportedSqlException extends RuntimeException {

    public UnsupportedSqlException(String message) {
        super(message);
    }

    public UnsupportedSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
