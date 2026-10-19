package com.sqlquery.slowlog;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowLogParserTest {

    @Test
    void parsesThreeEntriesAndAggregatesTwoClasses() throws Exception {
        List<SlowLogEntry> entries = SlowLogParser.parse(fixture());

        assertEquals(3, entries.size());
        assertEquals(1.2, entries.get(0).queryTime(), 0.001);
        assertEquals(1000000, entries.get(0).rowsExamined());
        assertTrue(entries.get(0).sql().startsWith("SELECT * FROM t_order"));

        List<SlowLogAggregator.Aggregate> aggregates = SlowLogAggregator.aggregate(entries);
        assertEquals(2, aggregates.size());
        assertEquals(2, aggregates.get(0).count());
        assertEquals(2.0, aggregates.get(0).totalTime(), 0.001);
    }

    @Test
    void normalizesLiteralsAndInLists() {
        String normalized = SlowLogAggregator.normalize(
                "SELECT * FROM t_order WHERE user_id IN (1, 2, 3) AND remark = 'x' AND amount > 100");

        assertTrue(normalized.contains("in (?)"));
        assertTrue(normalized.contains("remark = ?"));
        assertTrue(normalized.contains("amount > ?"));
    }

    private static Path fixture() throws URISyntaxException {
        return Path.of(SlowLogParserTest.class.getResource("/slow/example.log").toURI());
    }
}
