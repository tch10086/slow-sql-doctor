package com.sqlquery.report;

import com.sqlquery.advise.IndexSuggestion;
import com.sqlquery.bench.Measurement;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.model.TableAccess;
import com.sqlquery.rule.Issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 生成 Markdown / HTML 诊断报告。
 */
public final class ReportGenerator {

    private ReportGenerator() {
    }

    public static String markdown(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 慢 SQL 诊断报告\n\n");
        sb.append("- 生成时间：").append(LocalDateTime.now()).append('\n');
        sb.append("- 数据库：slow_sql_lab（MySQL 8.0）\n\n");

        sb.append("## 1. SQL\n\n```sql\n").append(result.sql()).append("\n```\n\n");
        sb.append("## 2. 执行计划\n\n");
        sb.append("| 表 | type | key | rows(估算) | filtered | 备注 |\n");
        sb.append("|---|---|---|---:|---:|---|\n");
        for (TableAccess table : result.plan().getTables()) {
            sb.append("| ").append(table.tableName())
                    .append(" | ").append(table.accessType())
                    .append(" | ").append(table.key() == null ? "NULL" : table.key())
                    .append(" | ").append(table.rowsExamined())
                    .append(" | ").append(table.filtered())
                    .append(" | ").append(md(table.usedColumns().toString()))
                    .append(" |\n");
        }

        ExplainPlan plan = result.plan();
        if (plan.isUsingFilesort() || plan.isUsingTemporary()) {
            sb.append("\n> 额外开销：");
            if (plan.isUsingFilesort()) {
                sb.append("`Using filesort` ");
            }
            if (plan.isUsingTemporary()) {
                sb.append("`Using temporary` ");
            }
            sb.append('\n');
        }

        sb.append("\n## 3. 问题\n\n");
        if (result.issues().isEmpty()) {
            sb.append("未命中规则。\n\n");
        } else {
            sb.append("| # | 规则 | 严重度 | 证据 | 建议 |\n");
            sb.append("|---|---|---|---|---|\n");
            int index = 1;
            for (Issue issue : result.issues()) {
                sb.append("| ").append(index++)
                        .append(" | ").append(issue.ruleId()).append(' ').append(md(issue.title()))
                        .append(" | ").append(issue.severity())
                        .append(" | ").append(md(issue.evidence()))
                        .append(" | ").append(md(issue.suggestion()))
                        .append(" |\n");
            }
        }

        sb.append("\n## 4. 索引建议\n\n");
        if (result.suggestions().isEmpty()) {
            sb.append("暂无索引建议。\n\n");
        } else {
            int index = 1;
            for (IndexSuggestion suggestion : result.suggestions()) {
                sb.append("### ").append(index++).append(") ").append(suggestion.table())
                        .append(" (").append(String.join(", ", suggestion.columns())).append(")\n\n");
                sb.append("```sql\n").append(suggestion.ddl()).append("\n```\n\n");
                sb.append("- 理由：").append(suggestion.reason()).append('\n');
                sb.append("- 风险：").append(suggestion.risk()).append("\n\n");
            }
        }

        sb.append("## 5. 实测\n\n");
        Measurement measurement = result.measurement();
        if (measurement == null) {
            sb.append("本次未执行原 SQL（例如笛卡尔积只做 EXPLAIN）。\n\n");
        } else {
            sb.append("| 指标 | 值 |\n|---|---:|\n");
            sb.append("| 中位耗时 | ").append(measurement.medianMs()).append(" ms |\n");
            sb.append("| Handler_reads | ").append(measurement.handlerReads()).append(" |\n");
            sb.append("| 返回行数 | ").append(measurement.rows()).append(" |\n\n");
        }

        sb.append("## 6. 复现\n\n");
        sb.append("```bash\n");
        sb.append("mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.sqlquery.Main \\\n");
        sb.append("  -Dexec.args=\"").append(result.sql().replace("\"", "\\\"")).append("\"\n");
        sb.append("```\n\n");

        sb.append("## 7. 已知局限\n\n");
        sb.append("- 只支持单条 `SELECT` / `PlainSelect`，子查询和 UNION 未覆盖。\n");
        sb.append("- `rows` 是估算值，报告同时给出 `Handler_reads` 实测。\n");
        sb.append("- 索引建议只生成 DDL，不自动执行。\n");

        return sb.toString();
    }

    public static String html(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<title>慢 SQL 诊断报告</title><style>")
                .append("body{font-family:-apple-system,Segoe UI,Microsoft YaHei,sans-serif;max-width:1000px;margin:32px auto;padding:0 20px;color:#222}")
                .append("h1{border-bottom:2px solid #333;padding-bottom:8px}")
                .append("table{border-collapse:collapse;width:100%;margin:12px 0}")
                .append("th,td{border:1px solid #ccc;padding:6px 8px;text-align:left;vertical-align:top}")
                .append("th{background:#f3f3f3}")
                .append("code,pre{background:#f6f8fa;border-radius:4px}")
                .append("pre{padding:12px;overflow:auto}")
                .append(".high{color:#b00020;font-weight:bold}.medium{color:#a15c00}.low{color:#555}")
                .append("</style></head><body>");

        sb.append("<h1>慢 SQL 诊断报告</h1>");
        sb.append("<p>生成时间：").append(htmlEscape(LocalDateTime.now().toString())).append("</p>");

        sb.append("<h2>1. SQL</h2><pre>").append(htmlEscape(result.sql())).append("</pre>");

        sb.append("<h2>2. 执行计划</h2><table><tr><th>表</th><th>type</th><th>key</th>")
                .append("<th>rows</th><th>filtered</th><th>used_columns</th></tr>");
        for (TableAccess table : result.plan().getTables()) {
            sb.append("<tr><td>").append(htmlEscape(table.tableName()))
                    .append("</td><td>").append(htmlEscape(table.accessType()))
                    .append("</td><td>").append(htmlEscape(table.key() == null ? "NULL" : table.key()))
                    .append("</td><td>").append(table.rowsExamined())
                    .append("</td><td>").append(table.filtered())
                    .append("</td><td>").append(htmlEscape(table.usedColumns().toString()))
                    .append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>3. 问题</h2>");
        if (result.issues().isEmpty()) {
            sb.append("<p>未命中规则。</p>");
        } else {
            sb.append("<table><tr><th>规则</th><th>严重度</th><th>证据</th><th>建议</th></tr>");
            for (Issue issue : result.issues()) {
                sb.append("<tr><td>").append(htmlEscape(issue.ruleId() + " " + issue.title()))
                        .append("</td><td class=\"").append(issue.severity().name().toLowerCase()).append("\">")
                        .append(issue.severity())
                        .append("</td><td>").append(htmlEscape(issue.evidence()))
                        .append("</td><td>").append(htmlEscape(issue.suggestion()))
                        .append("</td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<h2>4. 索引建议</h2>");
        if (result.suggestions().isEmpty()) {
            sb.append("<p>暂无索引建议。</p>");
        } else {
            for (IndexSuggestion suggestion : result.suggestions()) {
                sb.append("<h3>").append(htmlEscape(suggestion.table())).append(" (")
                        .append(htmlEscape(String.join(", ", suggestion.columns()))).append(")</h3>");
                sb.append("<pre>").append(htmlEscape(suggestion.ddl())).append("</pre>");
                sb.append("<p><b>理由：</b>").append(htmlEscape(suggestion.reason())).append("</p>");
                sb.append("<p><b>风险：</b>").append(htmlEscape(suggestion.risk())).append("</p>");
            }
        }

        sb.append("<h2>5. 实测</h2>");
        Measurement measurement = result.measurement();
        if (measurement == null) {
            sb.append("<p>本次未执行原 SQL。</p>");
        } else {
            sb.append("<table><tr><th>指标</th><th>值</th></tr>")
                    .append("<tr><td>中位耗时</td><td>").append(measurement.medianMs()).append(" ms</td></tr>")
                    .append("<tr><td>Handler_reads</td><td>").append(measurement.handlerReads()).append("</td></tr>")
                    .append("<tr><td>返回行数</td><td>").append(measurement.rows()).append("</td></tr>")
                    .append("</table>");
        }

        sb.append("<h2>6. 已知局限</h2><ul>")
                .append("<li>只支持单条 SELECT / PlainSelect。</li>")
                .append("<li>rows 是估算值，报告同时给出 Handler_reads 实测。</li>")
                .append("<li>索引建议只生成 DDL，不自动执行。</li>")
                .append("</ul></body></html>");
        return sb.toString();
    }

    /**
     * 写 Markdown + HTML 两个文件，返回 Markdown 路径。
     */
    public static Path write(AnalysisResult result, Path directory, String prefix) throws IOException {
        Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String base = prefix + "-" + stamp;

        Path md = directory.resolve(base + ".md");
        Path html = directory.resolve(base + ".html");
        Files.writeString(md, markdown(result), StandardCharsets.UTF_8);
        Files.writeString(html, html(result), StandardCharsets.UTF_8);
        return md;
    }

    private static String md(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }

    private static String htmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
