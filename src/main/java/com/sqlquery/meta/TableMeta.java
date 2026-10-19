package com.sqlquery.meta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 一张表的元数据 + 现有索引。
 */
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

    /**
     * 估算选择性：Cardinality / 行数。没有统计信息时返回 empty。
     */
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

    /**
     * 候选列是否已经是某个现有索引的前缀（被现有索引覆盖）。
     */
    public boolean hasIndexPrefix(List<String> candidate) {
        for (IndexMeta index : indexes) {
            if (index.columns().size() >= candidate.size()
                    && index.columns().subList(0, candidate.size()).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否存在与候选完全相同的索引。
     */
    public boolean hasIndexExactly(List<String> candidate) {
        return indexes.stream().anyMatch(i -> i.columns().equals(candidate));
    }

    /**
     * 候选索引是否是某个现有索引的前缀（建了会冗余）。
     */
    public boolean isPrefixOfExisting(List<String> candidate) {
        return hasIndexPrefix(candidate);
    }

    /**
     * 某个索引是否覆盖 needed 列（索引列 + 主键列）。
     */
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

    /**
     * 候选列 + 主键是否覆盖 selectColumns。
     */
    public boolean coversWith(List<String> candidate, Collection<String> selectColumns) {
        Set<String> available = new LinkedHashSet<>(candidate);
        available.addAll(primaryKeyColumns());
        return available.containsAll(selectColumns);
    }

    /**
     * 与候选相比已经冗余的现有索引（现有索引是候选的前缀）。
     * 例如已有 (a)，候选 (a,b) → (a) 冗余。
     */
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
