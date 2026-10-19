package com.sqlquery.advise;

import com.sqlquery.meta.ColumnMeta;
import com.sqlquery.meta.IndexMeta;
import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;
import com.sqlquery.parse.SqlAnalyzer;
import com.sqlquery.rule.QueryContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexAdvisorTest {

    private static final SqlAnalyzer ANALYZER = new SqlAnalyzer();
    private final IndexAdvisor advisor = new IndexAdvisor();

    @Test
    void suggestsEqualityThenOrderForQ01() {
        List<IndexSuggestion> suggestions = advisor.advise(context(
                "SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20",
                plan(access("t_order", "ref", "idx_user_id", 40)),
                Map.of("t_order", table())));

        assertEquals(1, suggestions.size());
        assertEquals(List.of("user_id", "create_time"), suggestions.get(0).columns());
        assertFalse(suggestions.get(0).covering());
        assertTrue(suggestions.get(0).ddl().contains("ADD INDEX"));
    }

    @Test
    void suggestsCoveringIndexForQ07() {
        List<IndexSuggestion> suggestions = advisor.advise(context(
                "SELECT id, user_id, amount FROM t_order WHERE user_id = 10086",
                plan(access("t_order", "ref", "idx_user_id", 40)),
                Map.of("t_order", table())));

        assertEquals(1, suggestions.size());
        assertEquals(List.of("user_id", "amount"), suggestions.get(0).columns());
        assertTrue(suggestions.get(0).covering());
    }

    @Test
    void skipsWhenExistingIndexAlreadyCoversCandidateForQ03() {
        List<IndexSuggestion> suggestions = advisor.advise(context(
                "SELECT * FROM t_order WHERE order_no = 202610180001",
                plan(access("t_order", "ALL", null, 997376)),
                Map.of("t_order", table())));

        assertTrue(suggestions.isEmpty());
    }

    @Test
    void mergesEqualityAndRangeForQ13() {
        List<IndexSuggestion> suggestions = advisor.advise(context(
                "SELECT * FROM t_order WHERE user_id IN (10086, 10087) AND create_time >= '2026-01-01'",
                plan(access("t_order", "range", "idx_user_id", 106)),
                Map.of("t_order", table())));

        assertEquals(1, suggestions.size());
        assertEquals(List.of("user_id", "create_time"), suggestions.get(0).columns());
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

    private static TableAccess access(String table, String type, String key, long rows) {
        return new TableAccess(table, type, List.of(), key, rows, rows, 100.0, List.of(), null);
    }

    private static TableMeta table() {
        Map<String, ColumnMeta> columns = new LinkedHashMap<>();
        columns.put("id", new ColumnMeta("id", "bigint", null));
        columns.put("user_id", new ColumnMeta("user_id", "bigint", null));
        columns.put("create_time", new ColumnMeta("create_time", "datetime", null));
        columns.put("amount", new ColumnMeta("amount", "decimal", null));
        columns.put("order_no", new ColumnMeta("order_no", "varchar", "utf8mb4"));

        Map<String, Long> cardinality = Map.of("user_id", 93_500L);
        return new TableMeta("t_order", 1_000_000, columns,
                List.of(new IndexMeta("PRIMARY", List.of("id"), true),
                        new IndexMeta("idx_user_id", List.of("user_id"), false),
                        new IndexMeta("idx_order_no", List.of("order_no"), false)),
                cardinality);
    }
}
