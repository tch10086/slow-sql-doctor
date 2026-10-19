package com.sqlquery.model;

import java.util.List;

/**
 * EXPLAIN JSON 里的一个 table 节点。
 * 注意：JOIN 时 table_name 可能是别名（u / o），不是真实表名。
 */
public record TableAccess(
        String tableName,
        String accessType,
        List<String> possibleKeys,
        String key,
        long rowsExamined,
        long rowsProduced,
        double filtered,
        List<String> usedColumns,
        String attachedCondition) {
}
