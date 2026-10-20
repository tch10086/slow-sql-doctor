package com.sqlquery.meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TableMetaLoader {

    private TableMetaLoader() {
    }

    public static Map<String, TableMeta> load(Connection conn, String schema) throws SQLException {
        Map<String, Long> rowCounts = loadRowCounts(conn, schema);
        Map<String, Map<String, ColumnMeta>> columns = loadColumns(conn, schema);
        Map<String, Map<String, List<String>>> indexColumns = new LinkedHashMap<>();
        Map<String, Map<String, Boolean>> indexUnique = new HashMap<>();
        Map<String, Map<String, Long>> cardinality = new HashMap<>();

        String sql = "SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE, CARDINALITY "
                + "FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = ? "
                + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    String index = rs.getString("INDEX_NAME");
                    String column = rs.getString("COLUMN_NAME");

                    indexColumns.computeIfAbsent(table, k -> new LinkedHashMap<>())
                            .computeIfAbsent(index, k -> new ArrayList<>())
                            .add(column);
                    indexUnique.computeIfAbsent(table, k -> new HashMap<>())
                            .put(index, rs.getInt("NON_UNIQUE") == 0);

                    long card = rs.getLong("CARDINALITY");
                    if (!rs.wasNull() && card > 0) {
                        cardinality.computeIfAbsent(table, k -> new HashMap<>())
                                .merge(column, card, Math::max);
                    }
                }
            }
        }

        Map<String, TableMeta> result = new LinkedHashMap<>();
        for (String table : columns.keySet()) {
            List<IndexMeta> indexes = new ArrayList<>();
            Map<String, List<String>> byName = indexColumns.getOrDefault(table, Map.of());
            for (Map.Entry<String, List<String>> entry : byName.entrySet()) {
                String name = entry.getKey();
                boolean unique = indexUnique.getOrDefault(table, Map.of()).getOrDefault(name, false);
                indexes.add(new IndexMeta(name, List.copyOf(entry.getValue()), unique));
            }

            result.put(table, new TableMeta(
                    table,
                    rowCounts.getOrDefault(table, 0L),
                    columns.get(table),
                    indexes,
                    cardinality.getOrDefault(table, Map.of())));
        }
        return result;
    }

    private static Map<String, Long> loadRowCounts(Connection conn, String schema) throws SQLException {
        Map<String, Long> rows = new HashMap<>();
        String sql = "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long count = rs.getLong("TABLE_ROWS");
                    rows.put(rs.getString("TABLE_NAME"), rs.wasNull() ? 0L : count);
                }
            }
        }
        return rows;
    }

    private static Map<String, Map<String, ColumnMeta>> loadColumns(Connection conn, String schema)
            throws SQLException {
        Map<String, Map<String, ColumnMeta>> columns = new LinkedHashMap<>();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_SET_NAME "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    String name = rs.getString("COLUMN_NAME");
                    columns.computeIfAbsent(table, k -> new LinkedHashMap<>())
                            .put(name, new ColumnMeta(
                                    name,
                                    rs.getString("DATA_TYPE"),
                                    rs.getString("CHARACTER_SET_NAME")));
                }
            }
        }
        return columns;
    }
}
