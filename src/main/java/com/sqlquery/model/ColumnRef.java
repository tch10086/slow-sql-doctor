package com.sqlquery.model;

public record ColumnRef(String table, String column) {

    @Override
    public String toString() {
        return table == null ? column : table + "." + column;
    }
}
