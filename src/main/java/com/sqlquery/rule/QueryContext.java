package com.sqlquery.rule;

import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.SqlFeatures;

import java.util.Map;

/**
 * 规则引擎的输入：SQL 特征 + 执行计划 + 表元数据。
 */
public record QueryContext(
        String sql,
        SqlFeatures features,
        ExplainPlan plan,
        Map<String, TableMeta> tables) {
}
