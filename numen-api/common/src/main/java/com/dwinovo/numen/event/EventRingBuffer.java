package com.dwinovo.numen.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bounded, monotonic event ring per companion — the external-brain event
 * channel backing the {@code get_events} MCP tool. Written from the server
 * tick thread, read from HTTP threads; synchronized because the two are
 * different threads.
 *
 * <p>Design mirrors {@link com.dwinovo.numen.task.TaskTerminalStore}: pure JDK,
 * bounded (oldest dropped), seq strictly increasing so a reader can resume
 * with {@code since_id} without missing or duplicating entries. The ring is
 * deliberately NOT durable — it is an observation window, not a log.
 */
public final class EventRingBuffer {

    /** Cap per companion; beyond this the oldest events are dropped. */
    public static final int DEFAULT_CAPACITY = 200;

    private final int capacity;
    private final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>();
    private long nextSeq = 1;
    private final Object lock = new Object();

    public EventRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public EventRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Append one event. Returns the assigned seq (0 if rejected as blank).
     * Event map shape: {@code {seq, ts, kind, data}} where data is a nested map.
     */
    public long append(String kind, long ts, Map<String, Object> data) {
        if (kind == null || kind.isBlank()) return 0;
        synchronized (lock) {
            long seq = nextSeq++;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seq", seq);
            entry.put("ts", ts);
            entry.put("kind", kind);
            entry.put("data", data == null ? Map.of() : data);
            events.addLast(entry);
            while (events.size() > capacity) {
                events.removeFirst();
            }
            return seq;
        }
    }

    /** Events with seq &gt; {@code sinceId}, oldest first. {@code sinceId &lt;= 0} returns from the head. */
    public List<Map<String, Object>> since(long sinceId) {
        synchronized (lock) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> e : events) {
                long seq = ((Number) e.get("seq")).longValue();
                if (seq > sinceId) out.add(e);
            }
            return out;
        }
    }

    /** The last assigned seq (0 when empty) — the next reader resume point. */
    public long lastSeq() {
        synchronized (lock) {
            return events.isEmpty() ? 0 : ((Number) events.getLast().get("seq")).longValue();
        }
    }

    public int size() {
        synchronized (lock) {
            return events.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            events.clear();
        }
    }
}
