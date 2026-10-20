package com.sqlquery.collect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;

import java.util.ArrayList;
import java.util.List;

// JSON 层级会随 JOIN、排序、分组变化，这里不按固定路径取，统一递归找 table 节点。
public final class ExplainParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExplainParser() {
    }

    public static ExplainPlan parse(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("EXPLAIN JSON is empty");
        }
        ExplainPlan plan = new ExplainPlan();
        walk(MAPPER.readTree(json), plan);
        return plan;
    }

    private static void walk(JsonNode node, ExplainPlan plan) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.hasNonNull("query_cost")) {
                plan.setQueryCost(node.get("query_cost").asText());
            }
            if (node.path("using_filesort").asBoolean(false)) {
                plan.setUsingFilesort(true);
            }
            if (node.path("using_temporary_table").asBoolean(false)) {
                plan.setUsingTemporary(true);
            }
            if (node.has("table") && node.get("table").isObject()) {
                plan.addTable(toTableAccess(node.get("table")));
            }
            node.fields().forEachRemaining(entry -> walk(entry.getValue(), plan));
        } else if (node.isArray()) {
            node.forEach(child -> walk(child, plan));
        }
    }

    private static TableAccess toTableAccess(JsonNode node) {
        return new TableAccess(
                text(node, "table_name", ""),
                text(node, "access_type", ""),
                strings(node.get("possible_keys")),
                node.hasNonNull("key") ? node.get("key").asText() : null,
                node.path("rows_examined_per_scan").asLong(0),
                node.path("rows_produced_per_join").asLong(0),
                filtered(node.get("filtered")),
                strings(node.get("used_columns")),
                node.hasNonNull("attached_condition") ? node.get("attached_condition").asText() : null);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static double filtered(JsonNode node) {
        if (node == null || node.isNull()) {
            return 100.0;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException e) {
            return 100.0;
        }
    }
}
