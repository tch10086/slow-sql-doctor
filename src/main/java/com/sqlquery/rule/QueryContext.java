package com.sqlquery.rule;

import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.SqlFeatures;

import java.util.Map;

public record QueryContext(
        String sql,
        SqlFeatures features,
        ExplainPlan plan,
        Map<String, TableMeta> tables) {
}
