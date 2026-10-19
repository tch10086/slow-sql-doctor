package com.sqlquery.rule.rules;

import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.List;

/**
 * R10：隐式笛卡尔积（JOIN 没有 ON 条件）。
 */
public final class CartesianJoinRule implements Rule {

    @Override
    public String id() {
        return "R10";
    }

    @Override
    public String title() {
        return "隐式笛卡尔积";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        if (!context.features().isCartesianJoin()) {
            return List.of();
        }
        return List.of(new Issue(
                id(),
                title(),
                severity(),
                "JOIN 没有 ON 连接条件，结果集是两表行数相乘",
                "补上 ON 条件；如果确实需要全组合，用 CROSS JOIN 明确表达",
                List.of()));
    }
}
