package com.sqlquery.rule.rules;

import com.sqlquery.meta.IndexMeta;
import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BackToTableRule implements Rule {

    @Override
    public String id() {
        return "R07";
    }

    @Override
    public String title() {
        return "回表";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<Issue> issues = new ArrayList<>();

        for (TableAccess table : context.plan().getTables()) {
            if (table.key() == null || !isIndexAccess(table.accessType())) {
                continue;
            }
            TableMeta meta = context.tables().get(table.tableName());
            if (meta == null) {
                continue;
            }
            IndexMeta index = meta.indexByName(table.key()).orElse(null);
            if (index == null) {
                continue;
            }

            List<String> selected = context.features().selectColumnsFor(table.tableName())
                    .stream().map(ColumnRef::column).distinct().toList();

            if (context.features().isSelectStar()) {
                issues.add(issue(table, "SELECT * 需要回表取整行", List.of()));
                continue;
            }
            if (selected.isEmpty()) {
                continue;
            }

            Set<String> available = new LinkedHashSet<>(index.columns());
            available.addAll(meta.primaryKeyColumns());

            List<String> missing = selected.stream().filter(c -> !available.contains(c)).toList();
            if (!missing.isEmpty()) {
                issues.add(issue(table, "key=" + table.key() + " 不覆盖列 " + missing, missing));
            }
        }
        return issues;
    }

    private static boolean isIndexAccess(String accessType) {
        return "ref".equalsIgnoreCase(accessType) || "range".equalsIgnoreCase(accessType)
                || "eq_ref".equalsIgnoreCase(accessType);
    }

    private static Issue issue(TableAccess table, String evidence, List<String> columns) {
        return new Issue(
                "R07",
                "回表",
                Severity.MEDIUM,
                table.tableName() + ": " + evidence,
                "考虑覆盖索引：把 SELECT 的列补进索引，减少回表",
                columns);
    }
}
