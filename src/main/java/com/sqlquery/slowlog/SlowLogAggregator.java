package com.sqlquery.slowlog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SlowLogAggregator {

    public record Aggregate(
            String fingerprint,
            String sampleSql,
            int count,
            double totalTime,
            double avgTime,
            double maxTime,
            long totalRowsExamined) {
    }

    private SlowLogAggregator() {
    }

    public static List<Aggregate> aggregate(List<SlowLogEntry> entries) {
        Map<String, Aggregate> map = new LinkedHashMap<>();

        for (SlowLogEntry entry : entries) {
            String normalized = normalize(entry.sql());
            String fingerprint = fingerprint(normalized);
            Aggregate current = map.get(fingerprint);
            if (current == null) {
                map.put(fingerprint, new Aggregate(fingerprint, entry.sql(), 1,
                        entry.queryTime(), entry.queryTime(), entry.queryTime(), entry.rowsExamined()));
            } else {
                int count = current.count() + 1;
                double total = current.totalTime() + entry.queryTime();
                map.put(fingerprint, new Aggregate(
                        fingerprint,
                        current.sampleSql(),
                        count,
                        total,
                        total / count,
                        Math.max(current.maxTime(), entry.queryTime()),
                        current.totalRowsExamined() + entry.rowsExamined()));
            }
        }

        List<Aggregate> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparingDouble(Aggregate::totalTime).reversed());
        return result;
    }

    
    public static String normalize(String sql) {
        String value = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        value = value.replaceAll("'(?:[^']|'')*'", "?");
        value = value.replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "?");
        value = value.replaceAll("(?i)\\bIN\\s*\\([^)]*\\)", "IN (?)");
        value = value.replaceAll("\\s+", " ").trim();
        return value.toLowerCase(Locale.ROOT);
    }

    public static String fingerprint(String normalizedSql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
