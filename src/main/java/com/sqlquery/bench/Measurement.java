package com.sqlquery.bench;

public record Measurement(long medianMs, long handlerReads, long rows) {
}
