package com.sqlquery.rule.rules;

import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.List;

/**
 * R09：深分页。LIMIT offset 很大时，前面 offset 行会被扫描后丢弃。
 */
public final class DeepPaginationRule implements Rule {

    private static final long MIN_OFFSET = 10_000;

    @Override
    public String id() {
        return "R09";
    }

    @Override
    public String title() {
        return "深分页";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        Long offset = context.features().getLimitOffset();
        if (offset == null || offset < MIN_OFFSET) {
            return List.of();
        }
        return List.of(new Issue(
                id(),
                title(),
                severity(),
                "LIMIT " + offset + ", " + context.features().getLimitRows(),
                "延迟关联（先查主键再 JOIN）或游标分页 WHERE id > last_id",
                List.of()));
    }
}
