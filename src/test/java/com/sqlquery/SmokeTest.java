package com.sqlquery;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 环境冒烟测试：确认 JUnit 5 + JSqlParser 依赖都能正常工作。
 * 对应手册 §2.4。
 */
class SmokeTest {

    @Test
    void jsqlparser_can_parse_select() throws Exception {
        Statement stmt = CCJSqlParserUtil.parse("select id from t_order where user_id = 1");
        assertInstanceOf(PlainSelect.class, stmt);
    }
}
