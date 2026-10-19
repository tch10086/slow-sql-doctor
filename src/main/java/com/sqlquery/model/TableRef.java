package com.sqlquery.model;

/**
 * FROM 里的表：真实表名 + 可选别名。
 */
public record TableRef(String tableName, String alias) {

    public String displayName() {
        return alias == null || alias.isBlank() || alias.equals(tableName)
                ? tableName
                : tableName + " " + alias;
    }
}
