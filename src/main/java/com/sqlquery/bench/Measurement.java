package com.sqlquery.bench;

/**
 * 一次测量的结果：中位耗时、Handler_read 差值、返回行数。
 */
public record Measurement(long medianMs, long handlerReads, long rows) {
}
