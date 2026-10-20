package com.sqlquery.rule.rules;

import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

public final class FullScanRule implements Rule {

    private static final long MIN_ROWS = 10_000;

    @Override
    public String id() {
        return "R01";
    }

    @Override
    public String title() {
        return "全表扫描";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<Issue> issues = new ArrayList<>();
        for (TableAccess table : context.plan().getTables()) {
            if (!"ALL".equalsIgnoreCase(table.accessType()) || table.rowsExamined() < MIN_ROWS) {
                continue;
            }
            Severity severity = table.rowsExamined() >= 100_000 ? Severity.HIGH : Severity.MEDIUM;
            issues.add(new Issue(
                    id(),
                    title(),
                    severity,
                    "type=ALL, rows=" + table.rowsExamined(),
                    "为 WHERE / JOIN 条件列建索引，或增加过滤条件缩小扫描范围",
                    columnNames(context, table.tableName())));
        }
        return issues;
    }

    private static List<String> columnNames(QueryContext context, String table) {
        List<String> names = new ArrayList<>();
        context.features().eqColumnsFor(table).forEach(c -> names.add(c.column()));
        context.features().rangeColumnsFor(table).forEach(c -> names.add(c.column()));
        return names;
    }
}
