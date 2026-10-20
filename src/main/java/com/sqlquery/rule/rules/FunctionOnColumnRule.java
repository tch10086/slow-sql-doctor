package com.sqlquery.rule.rules;

import com.sqlquery.model.ColumnRef;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

public final class FunctionOnColumnRule implements Rule {

    @Override
    public String id() {
        return "R03";
    }

    @Override
    public String title() {
        return "函数导致索引失效";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<Issue> issues = new ArrayList<>();
        for (ColumnRef column : context.features().getFunctionWrappedColumns()) {
            issues.add(new Issue(
                    id(),
                    title(),
                    severity(),
                    "WHERE 中 " + column + " 被函数包裹",
                    "把函数移到常量侧（如 create_time >= ? AND create_time < ?），或建函数索引",
                    List.of(column.column())));
        }
        return issues;
    }
}
