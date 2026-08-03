package com.dwinovo.numen.task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.UUID;

/**
 * Short-lived terminal receipts that survive a companion body/brain replacement.
 * External MCP callers have no death event, so a death must remain queryable by
 * the UUID they received before the body despawned and respawned.
 */
public final class TaskTerminalStore {

    private static final long RETENTION_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_PER_COMPANION = 32;
    private static final HashMap<UUID, Deque<Entry>> ENTRIES = new HashMap<>();

    private TaskTerminalStore() {}

    private record Entry(UUID companion, String companionName, TaskRecord record,
                         long observedGameTime, long expiresAtMillis) {}

    public static synchronized void remember(UUID companion, TaskRecord record, long observedGameTime) {
        remember(companion, null, record, observedGameTime);
    }

    /** Retain the display name too, so callers can query during the death window by name. */
    public static synchronized void remember(UUID companion, String companionName,
                                              TaskRecord record, long observedGameTime) {
        if (companion == null || record == null || !record.getState().isTerminal()) return;
        long now = System.currentTimeMillis();
        Deque<Entry> history = ENTRIES.computeIfAbsent(companion, ignored -> new ArrayDeque<>());
        purge(history, now);
        history.addLast(new Entry(companion, companionName, record, observedGameTime,
                now + RETENTION_MILLIS));
        while (history.size() > MAX_PER_COMPANION) history.removeFirst();
    }

    public static synchronized TaskRecord find(UUID companion, String taskId) {
        Deque<Entry> history = ENTRIES.get(companion);
        if (history == null || taskId == null || taskId.isBlank()) return null;
        purge(history, System.currentTimeMillis());
        for (Entry entry : history.reversed()) {
            if (taskId.equals(entry.record.publicId())) return entry.record;
        }
        return null;
    }

    /** Render a retained terminal snapshot for the MCP dead-body path. */
    public static synchronized String statusJson(UUID companion, String taskId) {
        Deque<Entry> history = ENTRIES.get(companion);
        if (history == null || taskId == null || taskId.isBlank()) return null;
        long nowMillis = System.currentTimeMillis();
        purge(history, nowMillis);
        for (Entry entry : history.reversed()) {
            if (taskId.equals(entry.record.publicId())) {
                TaskSnapshot snapshot = TaskSnapshot.capture(entry.record, entry.observedGameTime);
                return TaskResult.ok(snapshot.summary(), snapshot.toData()).toJson();
            }
        }
        return null;
    }

    /** Name-addressed variant used while the body is absent from the live roster. */
    public static synchronized String statusJsonByName(String companionName, String taskId) {
        if (companionName == null || companionName.isBlank() || taskId == null || taskId.isBlank()) return null;
        long now = System.currentTimeMillis();
        for (Deque<Entry> history : ENTRIES.values()) {
            purge(history, now);
            for (Entry entry : history.reversed()) {
                if (entry.companionName != null
                        && companionName.equalsIgnoreCase(entry.companionName)
                        && taskId.equals(entry.record.publicId())) {
                    TaskSnapshot snapshot = TaskSnapshot.capture(entry.record, entry.observedGameTime);
                    return TaskResult.ok(snapshot.summary(), snapshot.toData()).toJson();
                }
            }
        }
        return null;
    }

    private static void purge(Deque<Entry> history, long nowMillis) {
        while (!history.isEmpty() && history.peekFirst().expiresAtMillis() <= nowMillis) {
            history.removeFirst();
        }
    }
}
