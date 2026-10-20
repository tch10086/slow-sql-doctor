package com.sqlquery.meta;

import java.util.List;

public record IndexMeta(String name, List<String> columns, boolean unique) {

    public boolean isPrimary() {
        return "PRIMARY".equalsIgnoreCase(name);
    }
}
