package com.sqlquery.rule.rules;

import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.SqlFeatures;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * R05：LIKE '%x' 前导通配符。
 */
public final class LeadingWildcardRule implements Rule {

    @Override
    public String id() {
        return "R05";
    }

    @Override
    public String title() {
        return "前导通配符导致索引失效";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        SqlFeatures features = context.features();
        if (!features.isLeadingWildcard()) {
            return List.of();
        }

        List<String> columns = new ArrayList<>();
        for (ColumnRef column : features.getRangeColumns()) {
            columns.add(column.column());
        }

        return List.of(new Issue(
                id(),
                title(),
                severity(),
                "LIKE 以 % 开头，无法利用 B+ 树前缀",
                "改成后缀匹配 LIKE 'x%'、全文索引，或额外的倒排/搜索字段",
                columns));
    }
}
