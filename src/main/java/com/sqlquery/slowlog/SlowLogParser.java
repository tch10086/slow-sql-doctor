package com.sqlquery.slowlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 按 # Time 分段，SQL 以分号结束。字符串里带分号会误判，先用着，后面再换 pt-query-digest。
public final class SlowLogParser {

    private static final Pattern TIME = Pattern.compile("#\\s*Time:\\s*(\\S+)");
    private static final Pattern QUERY_TIME = Pattern.compile("Query_time:\\s*([0-9.]+)");
    private static final Pattern ROWS_EXAMINED = Pattern.compile("Rows_examined:\\s*(\\d+)");

    private SlowLogParser() {
    }

    public static List<SlowLogEntry> parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<SlowLogEntry> entries = new ArrayList<>();

        String time = "";
        double queryTime = 0;
        long rowsExamined = 0;
        StringBuilder sql = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# Time:")) {
                flush(entries, time, queryTime, rowsExamined, sql);
                Matcher matcher = TIME.matcher(line);
                time = matcher.find() ? matcher.group(1) : "";
                queryTime = 0;
                rowsExamined = 0;
                continue;
            }
            if (line.startsWith("# Query_time:")) {
                Matcher qt = QUERY_TIME.matcher(line);
                if (qt.find()) {
                    queryTime = Double.parseDouble(qt.group(1));
                }
                Matcher re = ROWS_EXAMINED.matcher(line);
                if (re.find()) {
                    rowsExamined = Long.parseLong(re.group(1));
                }
                continue;
            }
            if (line.startsWith("#") || line.startsWith("SET timestamp=")) {
                continue;
            }
            if (line.isBlank()) {
                continue;
            }

            sql.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                flush(entries, time, queryTime, rowsExamined, sql);
            }
        }
        flush(entries, time, queryTime, rowsExamined, sql);
        return entries;
    }

    private static void flush(List<SlowLogEntry> entries, String time, double queryTime,
                              long rowsExamined, StringBuilder sql) {
        String text = sql.toString().trim();
        if (!text.isEmpty()) {
            entries.add(new SlowLogEntry(time, queryTime, rowsExamined, text));
        }
        sql.setLength(0);
    }
}
