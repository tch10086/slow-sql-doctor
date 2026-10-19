package com.sqlquery.meta;

/**
 * 列元数据。
 */
public record ColumnMeta(String name, String dataType, String charset) {

    public boolean isStringType() {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("char") || type.contains("text") || type.contains("enum");
    }
}
