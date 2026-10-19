package com.sqlquery.parse;

import com.sqlquery.model.ColumnLiteral;
import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.LiteralKind;
import com.sqlquery.model.SqlFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalyzerTest {

    private final SqlAnalyzer analyzer = new SqlAnalyzer();

    @Test
    void extractsEqualityOrderAndStarForQ01() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20");

        assertTrue(features.isSelectStar());
        assertTrue(features.eqColumnsFor("t_order").contains(new ColumnRef("t_order", "user_id")));
        assertTrue(features.orderColumnsFor("t_order").contains(new ColumnRef("t_order", "create_time")));
        assertEquals(20L, features.getLimitRows().longValue());
        assertNull(features.getLimitOffset());
    }

    @Test
    void detectsFunctionWrappedColumnForQ02() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_order WHERE DATE(create_time) = '2026-03-01'");

        assertTrue(features.getFunctionWrappedColumns()
                .contains(new ColumnRef("t_order", "create_time")));
        assertTrue(features.eqColumnsFor("t_order").isEmpty());
    }

    @Test
    void detectsNumericLiteralForImplicitConversionQ03() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_order WHERE order_no = 202610180001");

        assertTrue(features.eqColumnsFor("t_order").contains(new ColumnRef("t_order", "order_no")));
        assertEquals(1, features.getComparisons().size());
        ColumnLiteral comparison = features.getComparisons().get(0);
        assertEquals(LiteralKind.NUMBER, comparison.literalKind());
        assertEquals("order_no", comparison.column().column());
    }

    @Test
    void detectsLeadingWildcardQ04() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_user WHERE username LIKE '%张%'");

        assertTrue(features.isLeadingWildcard());
        assertTrue(features.rangeColumnsFor("t_user").isEmpty());
    }

    @Test
    void resolvesJoinColumnOnDrivenTableQ11() {
        SqlFeatures features = analyzer.analyze(
                "SELECT o.id, o.amount FROM t_order o JOIN t_user u ON o.user_id = u.id WHERE u.city = '杭州'");

        assertEquals(2, features.getTables().size());
        assertTrue(features.joinColumnsFor("t_order").contains(new ColumnRef("t_order", "user_id")));
        assertTrue(features.eqColumnsFor("t_user").contains(new ColumnRef("t_user", "city")));
    }

    @Test
    void extractsEqualityAndRangeQ13() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_order WHERE user_id IN (10086, 10087) AND create_time >= '2026-01-01'");

        assertTrue(features.eqColumnsFor("t_order").contains(new ColumnRef("t_order", "user_id")));
        assertTrue(features.rangeColumnsFor("t_order").contains(new ColumnRef("t_order", "create_time")));
    }

    @Test
    void extractsGroupByQ14() {
        SqlFeatures features = analyzer.analyze(
                "SELECT status, COUNT(*) FROM t_order GROUP BY status");

        assertTrue(features.groupColumnsFor("t_order").contains(new ColumnRef("t_order", "status")));
    }

    @Test
    void detectsCartesianJoinQ12() {
        SqlFeatures features = analyzer.analyze(
                "SELECT * FROM t_order, t_user WHERE t_order.status = 1 AND t_user.age > 18");

        assertTrue(features.isCartesianJoin());
        assertEquals(2, features.getTables().size());
    }

    @Test
    void extractsDeepPaginationQ09() {
        SqlFeatures features = analyzer.analyze("SELECT * FROM t_order LIMIT 950000, 20");

        assertEquals(950000L, features.getLimitOffset().longValue());
        assertEquals(20L, features.getLimitRows().longValue());
    }
}
