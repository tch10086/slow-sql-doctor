package com.sqlquery.collect;

import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplainParserTest {

    @Test
    void parsesSingleTableWithFilesort() throws Exception {
        ExplainPlan plan = parse("q01_single_table.json");

        assertEquals("31.30", plan.getQueryCost());
        assertTrue(plan.isUsingFilesort());
        assertFalse(plan.isUsingTemporary());
        assertEquals(1, plan.getTables().size());

        TableAccess table = plan.getTables().get(0);
        assertEquals("t_order", table.tableName());
        assertEquals("ref", table.accessType());
        assertEquals("idx_user_id", table.key());
        assertEquals(40, table.rowsExamined());
        assertTrue(table.usedColumns().contains("amount"));
    }

    @Test
    void parsesNestedLoopJoin() throws Exception {
        ExplainPlan plan = parse("q11_join.json");

        assertEquals(2, plan.getTables().size());
        assertEquals("u", plan.getTables().get(0).tableName());
        assertEquals("o", plan.getTables().get(1).tableName());
        assertEquals("idx_city", plan.getTables().get(0).key());
        assertEquals("idx_user_id", plan.getTables().get(1).key());
    }

    @Test
    void parsesGroupingOperationTemporaryTable() throws Exception {
        ExplainPlan plan = parse("q14_group_by.json");

        assertTrue(plan.isUsingTemporary());
        assertFalse(plan.isUsingFilesort());
        assertEquals(1, plan.getTables().size());
        assertEquals(997376, plan.getTables().get(0).rowsExamined());
    }

    @Test
    void parsesConstAccess() throws Exception {
        ExplainPlan plan = parse("q_const.json");

        assertEquals("const", plan.getTables().get(0).accessType());
        assertEquals("PRIMARY", plan.getTables().get(0).key());
        assertEquals(1, plan.getTables().get(0).rowsExamined());
    }

    @Test
    void toleratesMissingOptionalFields() throws Exception {
        String json = "{\"query_block\":{\"table\":{\"table_name\":\"t\",\"access_type\":\"ALL\"}}}";

        ExplainPlan plan = ExplainParser.parse(json);

        assertEquals(1, plan.getTables().size());
        TableAccess table = plan.getTables().get(0);
        assertNull(table.key());
        assertTrue(table.possibleKeys().isEmpty());
        assertEquals(100.0, table.filtered(), 0.001);
        assertTrue(table.usedColumns().isEmpty());
    }

    private static ExplainPlan parse(String name) throws IOException {
        try (InputStream in = ExplainParserTest.class.getResourceAsStream("/plans/" + name)) {
            assertNotNull(in, "fixture not found: " + name);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ExplainParser.parse(json);
        }
    }
}
