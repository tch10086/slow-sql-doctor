package com.sqlquery.rule.rules;

import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

public final class IndexNotUsedRule implements Rule {

    @Override
    public String id() {
        return "R02";
    }

    @Override
    public String title() {
        return "索引未命中";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<Issue> issues = new ArrayList<>();
        for (TableAccess table : context.plan().getTables()) {
            if (table.key() != null || table.possibleKeys().isEmpty()) {
                continue;
            }
            issues.add(new Issue(
                    id(),
                    title(),
                    severity(),
                    "possible_keys=" + table.possibleKeys() + ", key=NULL",
                    "检查最左前缀、隐式转换、函数包裹或前导通配符；也可以刷新统计信息",
                    List.copyOf(table.possibleKeys())));
        }
        return issues;
    }
}
