package com.sqlquery;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SmokeTest {

    @Test
    void jsqlparser_can_parse_select() throws Exception {
        Statement stmt = CCJSqlParserUtil.parse("select id from t_order where user_id = 1");
        assertInstanceOf(PlainSelect.class, stmt);
    }
}
