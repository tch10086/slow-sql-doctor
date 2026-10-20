package com.sqlquery.rule.rules;

import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

public final class OrConditionRule implements Rule {

    @Override
    public String id() {
        return "R06";
    }

    @Override
    public String title() {
        return "OR 条件导致索引失效";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        if (!context.features().isHasOr()) {
            return List.of();
        }

        List<Issue> issues = new ArrayList<>();
        for (TableAccess table : context.plan().getTables()) {
            boolean noIndex = table.key() == null || "ALL".equalsIgnoreCase(table.accessType());
            if (!noIndex) {
                continue;
            }
            issues.add(new Issue(
                    id(),
                    title(),
                    severity(),
                    "WHERE 含 OR，且 " + table.tableName() + " 未走索引",
                    "拆成 UNION ALL，或给 OR 两侧的列都补索引后再评估",
                    List.of()));
        }
        return issues;
    }
}
