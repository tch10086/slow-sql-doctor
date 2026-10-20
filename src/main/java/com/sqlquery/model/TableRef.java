package com.sqlquery.model;

public record TableRef(String tableName, String alias) {

    public String displayName() {
        return alias == null || alias.isBlank() || alias.equals(tableName)
                ? tableName
                : tableName + " " + alias;
    }
}
