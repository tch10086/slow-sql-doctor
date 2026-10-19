package com.sqlquery.report;

import com.sqlquery.advise.IndexSuggestion;
import com.sqlquery.bench.Measurement;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.SqlFeatures;
import com.sqlquery.rule.Issue;

import java.util.List;

/**
 * 一次诊断的完整结果，报告生成器的输入。
 */
public record AnalysisResult(
        String sql,
        SqlFeatures features,
        ExplainPlan plan,
        List<Issue> issues,
        List<IndexSuggestion> suggestions,
        Measurement measurement) {

    public AnalysisResult {
        issues = List.copyOf(issues);
        suggestions = List.copyOf(suggestions);
    }
}
