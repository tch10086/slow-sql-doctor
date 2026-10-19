package com.sqlquery.model;

/**
 * 带表名的列引用。table 为 null 表示无法确定归属（多表且未限定）。
 */
public record ColumnRef(String table, String column) {

    @Override
    public String toString() {
        return table == null ? column : table + "." + column;
    }
}
