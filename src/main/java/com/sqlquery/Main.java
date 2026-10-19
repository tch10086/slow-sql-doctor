package com.sqlquery;

import com.sqlquery.advise.IndexSuggestion;
import com.sqlquery.bench.Benchmark;
import com.sqlquery.bench.Measurement;
import com.sqlquery.config.AppConfig;
import com.sqlquery.db.Db;
import com.sqlquery.model.ExplainPlan;
import com.sqlquery.report.AnalysisResult;
import com.sqlquery.report.ReportGenerator;
import com.sqlquery.rule.Issue;
import com.sqlquery.service.DiagnosisService;
import com.sqlquery.slowlog.SlowLogAggregator;
import com.sqlquery.slowlog.SlowLogEntry;
import com.sqlquery.slowlog.SlowLogParser;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

/**
 * CLI 入口。
 *
 * 用法：
 *   java -jar slow-sql-doctor.jar                     # 诊断默认 Q01
 *   java -jar slow-sql-doctor.jar "SELECT ..."        # 诊断指定 SQL
 *   java -jar slow-sql-doctor.jar -v "SELECT ..."     # 额外打印 EXPLAIN JSON
 *   java -jar slow-sql-doctor.jar bench "SELECT ..."  # 只测耗时
 *   java -jar slow-sql-doctor.jar slowlog <file>      # 聚合慢日志
 */
public final class Main {

    private static final String DEFAULT_SQL =
            "SELECT * FROM t_order WHERE user_id = 10086 ORDER BY create_time DESC LIMIT 20";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "slowlog".equals(args[0])) {
            if (args.length < 2) {
                System.err.println("usage: slowlog <path-to-slow.log>");
                return;
            }
            runSlowLog(Path.of(args[1]));
            return;
        }

        boolean verbose = args.length > 0 && "-v".equals(args[0]);
        String[] sqlArgs = verbose ? Arrays.copyOfRange(args, 1, args.length) : args;

        if (sqlArgs.length > 0 && "bench".equals(sqlArgs[0])) {
            runBench(String.join(" ", Arrays.copyOfRange(sqlArgs, 1, sqlArgs.length)));
            return;
        }

        String sql = sqlArgs.length == 0 ? DEFAULT_SQL : String.join(" ", sqlArgs);
        runAnalyze(sql, verbose);
    }

    private static void runAnalyze(String sql, boolean verbose) throws Exception {
        AppConfig config = AppConfig.load();
        try (Db db = new Db(config);
             Connection conn = db.getConnection()) {

            DiagnosisService service = new DiagnosisService();
            AnalysisResult result = service.analyze(conn, sql, true);

            System.out.println("SQL: " + sql);
            System.out.println();
            System.out.println("--- PLAN ---");
            System.out.println(result.plan().describe());
            if (verbose) {
                System.out.println("--- EXPLAIN JSON ---");
                System.out.println(result.plan().getRawJson());
            }

            System.out.println("--- ISSUES ---");
            if (result.issues().isEmpty()) {
                System.out.println("(none)");
            } else {
                for (Issue issue : result.issues()) {
                    System.out.printf("[%s] %s %s%n  evidence: %s%n  advice:   %s%n",
                            issue.severity(), issue.ruleId(), issue.title(),
                            issue.evidence(), issue.suggestion());
                }
            }

            System.out.println("--- INDEX SUGGESTIONS ---");
            if (result.suggestions().isEmpty()) {
                System.out.println("(none)");
            } else {
                for (IndexSuggestion suggestion : result.suggestions()) {
                    System.out.println(suggestion.ddl());
                    System.out.println("  reason: " + suggestion.reason());
                    System.out.println("  risk:   " + suggestion.risk());
                }
            }

            Measurement measurement = result.measurement();
            if (measurement != null) {
                System.out.printf("%nmeasure: median=%d ms, handler_reads=%d, rows=%d%n",
                        measurement.medianMs(), measurement.handlerReads(), measurement.rows());
            }

            String prefix = "q-" + Integer.toHexString(sql.hashCode());
            Path report = ReportGenerator.write(result, Path.of("docs", "reports"), prefix);
            System.out.println("report: " + report.toAbsolutePath());
        }
    }

    private static void runBench(String sql) throws Exception {
        if (sql.isBlank()) {
            System.err.println("usage: bench \"SELECT ...\"");
            return;
        }
        AppConfig config = AppConfig.load();
        try (Db db = new Db(config);
             Connection conn = db.getConnection()) {
            Measurement measurement = Benchmark.measure(conn, sql);
            System.out.printf("median=%d ms, handler_reads=%d, rows=%d%n",
                    measurement.medianMs(), measurement.handlerReads(), measurement.rows());
        }
    }

    private static void runSlowLog(Path path) throws Exception {
        List<SlowLogEntry> entries = SlowLogParser.parse(path);
        List<SlowLogAggregator.Aggregate> aggregates = SlowLogAggregator.aggregate(entries);

        System.out.printf("entries=%d, classes=%d%n", entries.size(), aggregates.size());
        System.out.printf("%-16s %6s %12s %10s %12s  %s%n",
                "fingerprint", "count", "total(s)", "avg(s)", "rows_examined", "sql");
        for (SlowLogAggregator.Aggregate aggregate : aggregates.stream().limit(20).toList()) {
            System.out.printf("%-16s %6d %12.3f %10.3f %12d  %s%n",
                    aggregate.fingerprint(),
                    aggregate.count(),
                    aggregate.totalTime(),
                    aggregate.avgTime(),
                    aggregate.totalRowsExamined(),
                    aggregate.sampleSql().replace('\n', ' '));
        }
    }
}
