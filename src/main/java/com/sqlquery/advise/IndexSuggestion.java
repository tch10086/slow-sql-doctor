package com.sqlquery.advise;

import java.util.List;

/**
 * 一条索引建议。
 */
public record IndexSuggestion(
        String table,
        List<String> columns,
        boolean covering,
        String ddl,
        String reason,
        String risk) {

    public IndexSuggestion {
        columns = List.copyOf(columns);
    }
}
