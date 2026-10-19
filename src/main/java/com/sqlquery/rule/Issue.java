package com.sqlquery.rule;

import java.util.List;

/**
 * 一条规则发现的问题。
 */
public record Issue(
        String ruleId,
        String title,
        Severity severity,
        String evidence,
        String suggestion,
        List<String> columns) {

    public Issue {
        columns = List.copyOf(columns);
    }
}
