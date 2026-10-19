package com.sqlquery.report;

import com.sqlquery.advise.IndexSuggestion;
import com.sqlquery.bench.Measurement;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorTest {

    @Test
    void markdownContainsPlanIssuesAndDdl() {
        AnalysisResult result = result();

        String markdown = ReportGenerator.markdown(result);

        assertTrue(markdown.contains("# 慢 SQL 诊断报告"));
        assertTrue(markdown.contains("R01 全表扫描"));
        assertTrue(markdown.contains("ALTER TABLE"));
        assertTrue(markdown.contains("中位耗时"));
        assertTrue(markdown.contains("Using filesort"));
    }

    @Test
    void htmlEscapesSqlAndRendersTables() {
        AnalysisResult result = result();

        String html = ReportGenerator.html(result);

        assertTrue(html.contains("<html"));
        assertTrue(html.contains("&lt;"), "SQL 里的 < 应被转义");
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("R01"));
    }

    private static AnalysisResult result() {
        ExplainPlan plan = new ExplainPlan();
        plan.addTable(new TableAccess("t_order", "ALL", List.of(), null, 997376, 997376,
                100.0, List.of("id", "create_time"), null));
        plan.setUsingFilesort(true);

        Issue issue = new Issue("R01", "全表扫描", Severity.HIGH,
                "type=ALL, rows=997376", "建索引", List.of("create_time"));
        IndexSuggestion suggestion = new IndexSuggestion("t_order", List.of("create_time"), false,
                "ALTER TABLE `t_order` ADD INDEX `idx_create_time` (`create_time`);",
                "排序列", "写放大");

        return new AnalysisResult(
                "SELECT * FROM t_order WHERE amount < 100 ORDER BY create_time",
                null,
                plan,
                List.of(issue),
                List.of(suggestion),
                new Measurement(12, 1000, 20));
    }
}
