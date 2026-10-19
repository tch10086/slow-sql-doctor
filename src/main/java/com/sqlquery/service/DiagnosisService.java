package com.sqlquery.service;

import com.sqlquery.advise.IndexAdvisor;
import com.sqlquery.advise.IndexSuggestion;
import com.sqlquery.bench.Benchmark;
import com.sqlquery.bench.Measurement;
import com.sqlquery.collect.ExplainCollector;
import com.sqlquery.meta.TableMeta;
import com.sqlquery.meta.TableMetaLoader;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.SqlFeatures;
import com.sqlquery.parse.SqlAnalyzer;
import com.sqlquery.report.AnalysisResult;
import com.sqlquery.rule.Issue;
import com.sqlquery.rule.QueryContext;
import com.sqlquery.rule.RuleEngine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 诊断编排：AST → EXPLAIN → 元数据 → 规则 → 索引建议 → 可选实测。
 */
public final class DiagnosisService {

    private final SqlAnalyzer analyzer = new SqlAnalyzer();
    private final ExplainCollector collector = new ExplainCollector();
    private final RuleEngine ruleEngine = RuleEngine.defaultEngine();
    private final IndexAdvisor advisor = new IndexAdvisor();

    public AnalysisResult analyze(Connection conn, String sql, boolean measure) throws SQLException {
        SqlFeatures features = analyzer.analyze(sql);
        ExplainPlan plan = collector.collect(conn, sql);

        // EXPLAIN 里 JOIN 的表名可能是别名（u / o），还原成真实表名后再查元数据
        plan.remapTableNames(alias -> {
            String real = features.resolveTable(alias);
            return real == null ? alias : real;
        });

        String schema = currentSchema(conn);
        Map<String, TableMeta> tables = TableMetaLoader.load(conn, schema);

        QueryContext context = new QueryContext(sql, features, plan, tables);
        List<Issue> issues = ruleEngine.analyze(context);
        List<IndexSuggestion> suggestions = advisor.advise(context);

        Measurement measurement = null;
        if (measure && !features.isCartesianJoin()) {
            measurement = Benchmark.measure(conn, sql);
        }

        return new AnalysisResult(sql, features, plan, issues, suggestions, measurement);
    }

    private static String currentSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
