package com.sqlquery.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析后的执行计划：整体标记 + 每个 table 节点的访问方式。
 */
public final class ExplainPlan {

    private String queryCost;
    private boolean usingFilesort;
    private boolean usingTemporary;
    private String rewrittenSql;
    private String rawJson;
    private final List<TableAccess> tables = new ArrayList<>();

    public void addTable(TableAccess table) {
        tables.add(table);
    }

    public List<TableAccess> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public String getQueryCost() {
        return queryCost;
    }

    public void setQueryCost(String queryCost) {
        this.queryCost = queryCost;
    }

    public boolean isUsingFilesort() {
        return usingFilesort;
    }

    public void setUsingFilesort(boolean usingFilesort) {
        this.usingFilesort = usingFilesort;
    }

    public boolean isUsingTemporary() {
        return usingTemporary;
    }

    public void setUsingTemporary(boolean usingTemporary) {
        this.usingTemporary = usingTemporary;
    }

    public String getRewrittenSql() {
        return rewrittenSql;
    }

    public void setRewrittenSql(String rewrittenSql) {
        this.rewrittenSql = rewrittenSql;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    /**
     * 给人看的摘要，方便命令行调试。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("query_cost=").append(queryCost == null ? "?" : queryCost)
                .append(", filesort=").append(usingFilesort)
                .append(", temporary=").append(usingTemporary)
                .append('\n');

        if (tables.isEmpty()) {
            sb.append("(no table)\n");
            return sb.toString();
        }

        for (TableAccess t : tables) {
            sb.append("- ").append(t.tableName())
                    .append(" type=").append(t.accessType())
                    .append(" key=").append(t.key() == null ? "NULL" : t.key())
                    .append(" rows=").append(t.rowsExamined())
                    .append(" filtered=").append(t.filtered())
                    .append(" used=").append(t.usedColumns())
                    .append('\n');
            if (t.attachedCondition() != null) {
                sb.append("  condition=").append(t.attachedCondition()).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
