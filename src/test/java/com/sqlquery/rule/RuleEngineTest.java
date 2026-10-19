package com.sqlquery.rule;

import com.sqlquery.meta.ColumnMeta;
import com.sqlquery.meta.IndexMeta;
import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;
import com.sqlquery.parse.SqlAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    private static final SqlAnalyzer ANALYZER = new SqlAnalyzer();
    private final RuleEngine engine = RuleEngine.defaultEngine();

    @Test
    void reportsLargeFullScan() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order ORDER BY create_time DESC LIMIT 10",
                plan(access("t_order", "ALL", null, 997376, List.of(), List.of("id", "create_time"))),
                Map.of("t_order", table("t_order", 1_000_000, Map.of("create_time", "datetime"), List.of()))));

        assertTrue(ids(issues).contains("R01"));
    }

    @Test
    void reportsFunctionOnColumn() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order WHERE DATE(create_time) = '2026-03-01'",
                plan(access("t_order", "ALL", null, 997376, List.of(), List.of("create_time"))),
                Map.of("t_order", table("t_order", 1_000_000, Map.of("create_time", "datetime"), List.of()))));

        assertTrue(ids(issues).contains("R03"));
    }

    @Test
    void reportsImplicitConversion() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order WHERE order_no = 202610180001",
                plan(access("t_order", "ALL", null, 997376, List.of("idx_order_no"), List.of("order_no"))),
                Map.of("t_order", table("t_order", 1_000_000,
                        Map.of("order_no", "varchar"), List.of(new IndexMeta("idx_order_no", List.of("order_no"), false))))));

        assertTrue(ids(issues).contains("R04"));
    }

    @Test
    void reportsLeadingWildcard() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_user WHERE username LIKE '%张%'",
                plan(access("t_user", "ALL", null, 100000, List.of("idx_username"), List.of("username"))),
                Map.of("t_user", table("t_user", 100_000,
                        Map.of("username", "varchar"), List.of(new IndexMeta("idx_username", List.of("username"), false))))));

        assertTrue(ids(issues).contains("R05"));
    }

    @Test
    void reportsDeepPagination() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order LIMIT 950000, 20",
                plan(access("t_order", "ALL", null, 997376, List.of(), List.of())),
                Map.of("t_order", table("t_order", 1_000_000, Map.of(), List.of()))));

        assertTrue(ids(issues).contains("R09"));
    }

    @Test
    void reportsCartesianJoin() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order, t_user WHERE t_order.status = 1 AND t_user.age > 18",
                plan(
                        access("t_order", "ALL", null, 997376, List.of(), List.of()),
                        access("t_user", "ALL", null, 100000, List.of(), List.of())),
                Map.of(
                        "t_order", table("t_order", 1_000_000, Map.of(), List.of()),
                        "t_user", table("t_user", 100_000, Map.of(), List.of()))));

        assertTrue(ids(issues).contains("R10"));
    }

    @Test
    void reportsBackToTableForSelectStar() {
        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order WHERE user_id = 10086",
                plan(access("t_order", "ref", "idx_user_id", 40, List.of("idx_user_id"), List.of("id", "user_id"))),
                Map.of("t_order", table("t_order", 1_000_000,
                        Map.of("user_id", "bigint"),
                        List.of(new IndexMeta("idx_user_id", List.of("user_id"), false))))));

        assertTrue(ids(issues).contains("R07"));
    }

    @Test
    void reportsFilesortAndFullScanTogether() {
        ExplainPlan plan = plan(access("t_order", "ALL", null, 997376, List.of(), List.of("create_time")));
        plan.setUsingFilesort(true);

        List<Issue> issues = engine.analyze(context(
                "SELECT * FROM t_order ORDER BY create_time DESC LIMIT 10",
                plan,
                Map.of("t_order", table("t_order", 1_000_000, Map.of(), List.of()))));

        Set<String> ids = ids(issues);
        assertTrue(ids.contains("R01"));
        assertTrue(ids.contains("R08"));
    }

    private static Set<String> ids(List<Issue> issues) {
        return issues.stream().map(Issue::ruleId).collect(Collectors.toSet());
    }

    private static QueryContext context(String sql, ExplainPlan plan, Map<String, TableMeta> tables) {
        return new QueryContext(sql, ANALYZER.analyze(sql), plan, tables);
    }

    private static ExplainPlan plan(TableAccess... tables) {
        ExplainPlan plan = new ExplainPlan();
        for (TableAccess table : tables) {
            plan.addTable(table);
        }
        return plan;
    }

    private static TableAccess access(String table, String type, String key, long rows,
                                      List<String> possibleKeys, List<String> usedColumns) {
        return new TableAccess(table, type, possibleKeys, key, rows, rows, 100.0, usedColumns, null);
    }

    private static TableMeta table(String name, long rows, Map<String, String> columnTypes, List<IndexMeta> indexes) {
        Map<String, ColumnMeta> columns = new LinkedHashMap<>();
        columnTypes.forEach((column, type) -> columns.put(column, new ColumnMeta(column, type, "utf8mb4")));
        return new TableMeta(name, rows, columns, indexes, Map.of());
    }
}
