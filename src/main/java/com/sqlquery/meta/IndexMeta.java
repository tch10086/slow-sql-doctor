package com.sqlquery.meta;

import java.util.List;

/**
 * 索引元数据：列顺序、是否唯一。
 */
public record IndexMeta(String name, List<String> columns, boolean unique) {

    public boolean isPrimary() {
        return "PRIMARY".equalsIgnoreCase(name);
    }
}
