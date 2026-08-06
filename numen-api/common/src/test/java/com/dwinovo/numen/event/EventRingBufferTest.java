package com.dwinovo.numen.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure contract tests for the per-companion event ring (no Minecraft). */
class EventRingBufferTest {

    @Test
    void appendsAssignMonotonicSeqsAndReadsBack() {
        EventRingBuffer ring = new EventRingBuffer(50);
        long s1 = ring.append("entered_water", 100, Map.of("x", 1));
        long s2 = ring.append("air_low", 120, Map.of("air", 40));
        long s3 = ring.append("task_finished", 140, Map.of("status", "done"));

        assertEquals(1L, s1);
        assertEquals(2L, s2);
        assertEquals(3L, s3);
        assertEquals(3L, ring.lastSeq());

        List<Map<String, Object>> all = ring.since(0);
        assertEquals(3, all.size());
        assertEquals("entered_water", all.get(0).get("kind"));
        assertEquals(1L, ((Number) all.get(0).get("seq")).longValue());
        assertEquals(100L, ((Number) all.get(0).get("ts")).longValue());
        assertEquals(Map.of("x", 1), all.get(0).get("data"));
    }

    @Test
    void sinceResumesWithoutGapsOrDuplicates() {
        EventRingBuffer ring = new EventRingBuffer(50);
        ring.append("a", 10, Map.of());
        ring.append("b", 20, Map.of());
        long last = ring.append("c", 30, Map.of());

        List<Map<String, Object>> resumed = ring.since(1);
        assertEquals(2, resumed.size());
        assertEquals("b", resumed.get(0).get("kind"));
        assertEquals("c", resumed.get(1).get("kind"));
        assertEquals(last, ring.lastSeq());
    }

    @Test
    void capacityDropsOldest() {
        EventRingBuffer ring = new EventRingBuffer(3);
        ring.append("a", 1, Map.of());
        ring.append("b", 2, Map.of());
        ring.append("c", 3, Map.of());
        ring.append("d", 4, Map.of());

        List<Map<String, Object>> all = ring.since(0);
        assertEquals(3, all.size());
        assertEquals("b", all.get(0).get("kind"));
        assertEquals("d", all.get(2).get("kind"));
        // seq keeps increasing even though old entries were dropped
        assertEquals(4L, ring.lastSeq());
        assertTrue(ring.since(3).size() == 1);
    }

    @Test
    void blankKindIsRejectedAndEmptyRingReportsZero() {
        EventRingBuffer ring = new EventRingBuffer(10);
        assertEquals(0L, ring.append("", 1, Map.of()));
        assertEquals(0L, ring.lastSeq());
        assertTrue(ring.since(0).isEmpty());
    }
}
