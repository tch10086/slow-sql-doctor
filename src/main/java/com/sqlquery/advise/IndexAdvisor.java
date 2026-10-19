package com.sqlquery.advise;

import com.sqlquery.meta.TableMeta;
import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.QueryContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 索引建议：
 *   等值列（含 JOIN 关联列，按区分度降序） → 范围列（最多 1 个） → 排序列/分组列
 *   能覆盖 SELECT 列时追加为覆盖索引
 *   与现有索引比对，跳过已被前缀覆盖的候选
 *
 * 只生成 DDL，不执行。
 */
public final class IndexAdvisor {

    private static final int MAX_COLUMNS = 5;
    private static final int MAX_COVERING_EXTRA = 3;

    public List<IndexSuggestion> advise(QueryContext context) {
        if (context.features().isCartesianJoin()) {
            return List.of();
        }

        List<IndexSuggestion> suggestions = new ArrayList<>();
        List<TableAccess> planTables = context.plan().getTables();

        for (int position = 0; position < planTables.size(); position++) {
            TableAccess access = planTables.get(position);
            TableMeta meta = context.tables().get(access.tableName());
            if (meta == null) {
                continue;
            }

            LinkedHashSet<String> equality = new LinkedHashSet<>();
            // 执行计划里第一张是驱动表，后面的才是被驱动表；JOIN 关联列只对后者有意义
            if (position > 0) {
                context.features().joinColumnsFor(meta.tableName()).forEach(c -> equality.add(c.column()));
            }
            context.features().eqColumnsFor(meta.tableName()).forEach(c -> equality.add(c.column()));

            List<String> equalityColumns = equality.stream()
                    .sorted(Comparator.comparingDouble((String c) -> meta.selectivity(c).orElse(-1.0)).reversed())
                    .toList();

            List<ColumnRef> ranges = context.features().rangeColumnsFor(meta.tableName());
            List<ColumnRef> orders = context.features().orderColumnsFor(meta.tableName());
            List<ColumnRef> groups = context.features().groupColumnsFor(meta.tableName());

            LinkedHashSet<String> candidate = new LinkedHashSet<>(equalityColumns);
            if (!ranges.isEmpty()) {
                candidate.add(ranges.get(0).column());
            }
            if (ranges.isEmpty()) {
                orders.forEach(c -> candidate.add(c.column()));
                groups.forEach(c -> candidate.add(c.column()));
            }

            if (candidate.isEmpty()) {
                continue;
            }

            List<String> baseColumns = candidate.stream().limit(MAX_COLUMNS).collect(Collectors.toList());

            List<String> columns = baseColumns;
            boolean covering = false;
            if (!context.features().isSelectStar()) {
                List<String> selected = context.features().selectColumnsFor(meta.tableName()).stream()
                        .map(ColumnRef::column).distinct().toList();
                List<String> missing = selected.stream()
                        .filter(c -> !baseColumns.contains(c) && !meta.primaryKeyColumns().contains(c))
                        .toList();
                if (missing.isEmpty() && !selected.isEmpty()) {
                    covering = true;
                } else if (!missing.isEmpty() && missing.size() <= MAX_COVERING_EXTRA) {
                    List<String> extended = new ArrayList<>(baseColumns);
                    extended.addAll(missing);
                    columns = extended.stream().limit(MAX_COLUMNS).toList();
                    covering = true;
                }
            }

            // 已经建成完全相同的索引，或候选只是现有索引的前缀 → 不用建
            if (meta.hasIndexPrefix(columns) || meta.hasIndexExactly(columns)) {
                continue;
            }

            String name = indexName(meta, columns);
            String ddl = "ALTER TABLE `" + meta.tableName() + "` ADD INDEX `" + name + "` ("
                    + columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", ")) + ");";

            suggestions.add(new IndexSuggestion(
                    meta.tableName(),
                    columns,
                    covering,
                    ddl,
                    reason(equalityColumns, ranges, orders, groups, covering),
                    risk(meta, columns, covering)));
        }

        return suggestions;
    }

    private static String reason(List<String> equality,
                                 List<ColumnRef> ranges,
                                 List<ColumnRef> orders,
                                 List<ColumnRef> groups,
                                 boolean covering) {
        List<String> parts = new ArrayList<>();
        if (!equality.isEmpty()) {
            parts.add("等值列 " + equality);
        }
        if (!ranges.isEmpty()) {
            parts.add("范围列 " + ranges.get(0).column() + "（范围列之后的列不能再用于定位）");
        }
        if (!orders.isEmpty()) {
            parts.add("排序列 " + orders.stream().map(ColumnRef::column).toList());
        }
        if (!groups.isEmpty()) {
            parts.add("分组列 " + groups.stream().map(ColumnRef::column).toList());
        }
        if (covering) {
            parts.add("覆盖 SELECT 列（避免回表）");
        }
        return String.join("；", parts);
    }

    private static String risk(TableMeta meta, List<String> columns, boolean covering) {
        List<String> risks = new ArrayList<>();
        risks.add("写入时多维护一棵索引，存在写放大");
        if (covering) {
            risks.add("覆盖索引更宽，注意索引大小和 buffer pool 占用");
        }
        List<String> redundant = meta.redundantPrefixIndexes(columns);
        if (!redundant.isEmpty()) {
            risks.add("现有索引 " + redundant + " 可能成为冗余前缀，评估后再决定是否删除");
        }
        double maxSelectivity = columns.stream()
                .map(meta::selectivity)
                .filter(java.util.Optional::isPresent)
                .mapToDouble(java.util.Optional::get)
                .max()
                .orElse(1.0);
        if (columns.size() == 1 && maxSelectivity < 0.1) {
            risks.add("区分度较低，单独建索引收益有限，建议先 EXPLAIN 验证");
        }
        return String.join("；", risks) + "。";
    }

    private static String indexName(TableMeta meta, List<String> columns) {
        String base = "idx_" + columns.stream()
                .map(c -> c.replaceAll("[^a-zA-Z0-9_]", "_"))
                .collect(Collectors.joining("_"));
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }

        String candidate = base;
        int suffix = 2;
        Set<String> existing = meta.indexes().stream().map(i -> i.name().toLowerCase()).collect(Collectors.toSet());
        while (existing.contains(candidate.toLowerCase())) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }
}
