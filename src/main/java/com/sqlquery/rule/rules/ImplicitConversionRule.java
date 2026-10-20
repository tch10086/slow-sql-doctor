package com.sqlquery.rule.rules;

import com.sqlquery.meta.ColumnMeta;
import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ColumnLiteral;
import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.LiteralKind;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

public final class ImplicitConversionRule implements Rule {

    @Override
    public String id() {
        return "R04";
    }

    @Override
    public String title() {
        return "隐式类型转换";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<Issue> issues = new ArrayList<>();
        for (ColumnLiteral comparison : context.features().getComparisons()) {
            if (comparison.literalKind() != LiteralKind.NUMBER) {
                continue;
            }
            ColumnRef column = comparison.column();
            if (column.table() == null) {
                continue;
            }
            TableMeta table = context.tables().get(column.table());
            if (table == null) {
                continue;
            }
            ColumnMeta meta = table.column(column.column()).orElse(null);
            if (meta == null || !meta.isStringType()) {
                continue;
            }
            issues.add(new Issue(
                    id(),
                    title(),
                    severity(),
                    column + "(" + meta.dataType() + ") 与数字字面量比较",
                    "给字面量加引号，例如 " + column.column() + " = '123'；或统一列类型",
                    List.of(column.column())));
        }
        return issues;
    }
}
