package com.sqlquery.meta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TableMeta {

    private final String tableName;
    private final long rowCount;
    private final Map<String, ColumnMeta> columns;
    private final List<IndexMeta> indexes;
    private final Map<String, Long> columnCardinality;

    public TableMeta(String tableName,
                     long rowCount,
                     Map<String, ColumnMeta> columns,
                     List<IndexMeta> indexes,
                     Map<String, Long> columnCardinality) {
        this.tableName = tableName;
        this.rowCount = rowCount;
        this.columns = Map.copyOf(columns);
        this.indexes = List.copyOf(indexes);
        this.columnCardinality = Map.copyOf(columnCardinality);
    }

    public String tableName() {
        return tableName;
    }

    public long rowCount() {
        return rowCount;
    }

    public Optional<ColumnMeta> column(String name) {
        return Optional.ofNullable(columns.get(name));
    }

    public List<IndexMeta> indexes() {
        return indexes;
    }

    
    public Optional<Double> selectivity(String column) {
        Long cardinality = columnCardinality.get(column);
        if (cardinality == null || cardinality <= 0 || rowCount <= 0) {
            return Optional.empty();
        }
        return Optional.of(Math.min(1.0, cardinality / (double) rowCount));
    }

    public Optional<IndexMeta> indexByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return indexes.stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst();
    }

    
    public boolean hasIndexPrefix(List<String> candidate) {
        for (IndexMeta index : indexes) {
            if (index.columns().size() >= candidate.size()
                    && index.columns().subList(0, candidate.size()).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    
    public boolean hasIndexExactly(List<String> candidate) {
        return indexes.stream().anyMatch(i -> i.columns().equals(candidate));
    }

    
    public boolean isPrefixOfExisting(List<String> candidate) {
        return hasIndexPrefix(candidate);
    }

    
    public boolean covers(String indexName, Collection<String> needed) {
        IndexMeta index = indexByName(indexName).orElse(null);
        if (index == null) {
            return false;
        }
        Set<String> available = new LinkedHashSet<>(index.columns());
        primaryKeyColumns().forEach(available::add);
        return available.containsAll(needed);
    }

    public List<String> primaryKeyColumns() {
        List<String> primary = new ArrayList<>();
        for (IndexMeta index : indexes) {
            if (index.isPrimary()) {
                primary.addAll(index.columns());
            }
        }
        return primary;
    }

    
    public boolean coversWith(List<String> candidate, Collection<String> selectColumns) {
        Set<String> available = new LinkedHashSet<>(candidate);
        available.addAll(primaryKeyColumns());
        return available.containsAll(selectColumns);
    }

    
    public List<String> redundantPrefixIndexes(List<String> candidate) {
        List<String> result = new ArrayList<>();
        for (IndexMeta index : indexes) {
            if (index.isPrimary()) {
                continue;
            }
            if (candidate.size() >= index.columns().size()
                    && candidate.subList(0, index.columns().size()).equals(index.columns())) {
                result.add(index.name());
            }
        }
        return result;
    }
}
