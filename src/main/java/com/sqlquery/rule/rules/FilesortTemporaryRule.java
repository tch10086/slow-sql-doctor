package com.sqlquery.rule.rules;

import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.Rule;
import com.sqlquery.rule.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * R08：文件排序 / 临时表。
 */
public final class FilesortTemporaryRule implements Rule {

    @Override
    public String id() {
        return "R08";
    }

    @Override
    public String title() {
        return "文件排序或临时表";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Issue> check(QueryContext context) {
        List<String> evidence = new ArrayList<>();
        if (context.plan().isUsingFilesort()) {
            evidence.add("Using filesort");
        }
        if (context.plan().isUsingTemporary()) {
            evidence.add("Using temporary");
        }
        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Issue(
                id(),
                title(),
                severity(),
                String.join(" + ", evidence),
                "调整联合索引列序（等值 → 范围 → 排序），或减少排序/分组的数据量",
                List.of()));
    }
}
