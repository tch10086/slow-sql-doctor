package com.sqlquery.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 SQL AST 抽出来的、规则引擎和索引建议需要的特征。
 */
public final class SqlFeatures {

    private final String normalizedSql;
    private final List<TableRef> tables = new ArrayList<>();
    private final Map<String, String> aliasToTable = new LinkedHashMap<>();

    private boolean selectStar;
    private final List<ColumnRef> selectColumns = new ArrayList<>();
    private final List<ColumnRef> eqColumns = new ArrayList<>();
    private final List<ColumnRef> rangeColumns = new ArrayList<>();
    private final List<ColumnRef> joinColumns = new ArrayList<>();
    private final List<ColumnRef> orderColumns = new ArrayList<>();
    private final List<ColumnRef> groupColumns = new ArrayList<>();
    private final List<ColumnRef> functionWrappedColumns = new ArrayList<>();
    private final List<ColumnLiteral> comparisons = new ArrayList<>();

    private boolean leadingWildcard;
    private boolean hasOr;
    private boolean cartesianJoin;
    private Long limitOffset;
    private Long limitRows;

    public SqlFeatures(String normalizedSql) {
        this.normalizedSql = normalizedSql;
    }

    public void addTable(TableRef table) {
        tables.add(table);
        aliasToTable.put(table.tableName(), table.tableName());
        if (table.alias() != null && !table.alias().isBlank()) {
            aliasToTable.put(table.alias(), table.tableName());
        }
    }

    /**
     * 别名或真实表名 → 真实表名；找不到返回 null。
     */
    public String resolveTable(String name) {
        return name == null ? null : aliasToTable.get(name);
    }

    public boolean hasTable(String tableName) {
        return tables.stream().anyMatch(t -> t.tableName().equals(tableName));
    }

    public Set<String> tableNames() {
        Set<String> names = new LinkedHashSet<>();
        for (TableRef table : tables) {
            names.add(table.tableName());
        }
        return names;
    }

    public List<ColumnRef> columnsFor(String tableName, List<ColumnRef> source) {
        List<ColumnRef> result = new ArrayList<>();
        for (ColumnRef ref : source) {
            if (tableName.equals(ref.table())) {
                result.add(ref);
            }
        }
        return result;
    }

    public List<ColumnRef> eqColumnsFor(String table) {
        return columnsFor(table, eqColumns);
    }

    public List<ColumnRef> rangeColumnsFor(String table) {
        return columnsFor(table, rangeColumns);
    }

    public List<ColumnRef> joinColumnsFor(String table) {
        return columnsFor(table, joinColumns);
    }

    public List<ColumnRef> orderColumnsFor(String table) {
        return columnsFor(table, orderColumns);
    }

    public List<ColumnRef> groupColumnsFor(String table) {
        return columnsFor(table, groupColumns);
    }

    public List<ColumnRef> selectColumnsFor(String table) {
        return columnsFor(table, selectColumns);
    }

    public void addSelectColumn(ColumnRef ref) {
        addUnique(selectColumns, ref);
    }

    public void addEqColumn(ColumnRef ref) {
        addUnique(eqColumns, ref);
    }

    public void addRangeColumn(ColumnRef ref) {
        addUnique(rangeColumns, ref);
    }

    public void addJoinColumn(ColumnRef ref) {
        addUnique(joinColumns, ref);
    }

    public void addOrderColumn(ColumnRef ref) {
        addUnique(orderColumns, ref);
    }

    public void addGroupColumn(ColumnRef ref) {
        addUnique(groupColumns, ref);
    }

    public void addFunctionColumn(ColumnRef ref) {
        addUnique(functionWrappedColumns, ref);
    }

    public void addComparison(ColumnRef column, LiteralKind kind) {
        comparisons.add(new ColumnLiteral(column, kind));
    }

    private static void addUnique(List<ColumnRef> list, ColumnRef ref) {
        if (ref != null && ref.column() != null && !list.contains(ref)) {
            list.add(ref);
        }
    }

    public String getNormalizedSql() {
        return normalizedSql;
    }

    public List<TableRef> getTables() {
        return List.copyOf(tables);
    }

    public boolean isSelectStar() {
        return selectStar;
    }

    public void setSelectStar(boolean selectStar) {
        this.selectStar = selectStar;
    }

    public List<ColumnRef> getSelectColumns() {
        return List.copyOf(selectColumns);
    }

    public List<ColumnRef> getEqColumns() {
        return List.copyOf(eqColumns);
    }

    public List<ColumnRef> getRangeColumns() {
        return List.copyOf(rangeColumns);
    }

    public List<ColumnRef> getJoinColumns() {
        return List.copyOf(joinColumns);
    }

    public List<ColumnRef> getOrderColumns() {
        return List.copyOf(orderColumns);
    }

    public List<ColumnRef> getGroupColumns() {
        return List.copyOf(groupColumns);
    }

    public List<ColumnRef> getFunctionWrappedColumns() {
        return List.copyOf(functionWrappedColumns);
    }

    public List<ColumnLiteral> getComparisons() {
        return List.copyOf(comparisons);
    }

    public boolean isLeadingWildcard() {
        return leadingWildcard;
    }

    public void setLeadingWildcard(boolean leadingWildcard) {
        this.leadingWildcard = leadingWildcard;
    }

    public boolean isHasOr() {
        return hasOr;
    }

    public void setHasOr(boolean hasOr) {
        this.hasOr = hasOr;
    }

    public boolean isCartesianJoin() {
        return cartesianJoin;
    }

    public void setCartesianJoin(boolean cartesianJoin) {
        this.cartesianJoin = cartesianJoin;
    }

    public Long getLimitOffset() {
        return limitOffset;
    }

    public void setLimitOffset(Long limitOffset) {
        this.limitOffset = limitOffset;
    }

    public Long getLimitRows() {
        return limitRows;
    }

    public void setLimitRows(Long limitRows) {
        this.limitRows = limitRows;
    }
}
