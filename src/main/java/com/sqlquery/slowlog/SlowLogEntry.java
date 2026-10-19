package com.sqlquery.slowlog;

public record SlowLogEntry(String time, double queryTime, long rowsExamined, String sql) {
}
