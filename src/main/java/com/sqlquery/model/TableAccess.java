package com.sqlquery.model;

import java.util.List;

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
