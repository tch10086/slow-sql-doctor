package com.sqlquery.rule;

import com.sqlquery.rule.rules.BackToTableRule;
import com.sqlquery.rule.rules.CartesianJoinRule;
import com.sqlquery.rule.rules.DeepPaginationRule;
import com.sqlquery.rule.rules.FilesortTemporaryRule;
import com.sqlquery.rule.rules.FullScanRule;
import com.sqlquery.rule.rules.FunctionOnColumnRule;
import com.sqlquery.rule.rules.ImplicitConversionRule;
import com.sqlquery.rule.rules.IndexNotUsedRule;
import com.sqlquery.rule.rules.LeadingWildcardRule;
import com.sqlquery.rule.rules.OrConditionRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 单条规则出异常只记日志，不能让整次诊断挂掉；收集完再去重、按严重度排。
public final class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static RuleEngine defaultEngine() {
        return new RuleEngine(List.of(
                new FullScanRule(),
                new IndexNotUsedRule(),
                new FunctionOnColumnRule(),
                new ImplicitConversionRule(),
                new LeadingWildcardRule(),
                new OrConditionRule(),
                new BackToTableRule(),
                new FilesortTemporaryRule(),
                new DeepPaginationRule(),
                new CartesianJoinRule()));
    }

    public List<Rule> rules() {
        return rules;
    }

    public List<Issue> analyze(QueryContext context) {
        Map<String, Issue> dedup = new LinkedHashMap<>();

        for (Rule rule : rules) {
            try {
                for (Issue issue : rule.check(context)) {
                    String key = issue.ruleId() + "|" + issue.columns();
                    dedup.putIfAbsent(key, issue);
                }
            } catch (RuntimeException e) {
                // 单条规则失败不影响整体诊断
                log.warn("rule {} failed: {}", rule.id(), e.toString());
            }
        }

        List<Issue> issues = new ArrayList<>(dedup.values());
        issues.sort(Comparator.comparing(Issue::severity));
        return issues;
    }
}
