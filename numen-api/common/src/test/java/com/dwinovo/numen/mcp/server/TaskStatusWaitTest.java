package com.dwinovo.numen.mcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the {@code task_status} blocking-wait semantics
 * ({@code wait_seconds}). {@link McpServer#waitForTerminal} is the pure
 * polling core (no Minecraft objects), so the three wait behaviors are
 * testable headlessly with a fake snapshot supplier:
 *
 * <ul>
 *   <li>{@code wait_seconds=0} — single snapshot, never blocks;</li>
 *   <li>terminal snapshot — returns immediately regardless of the budget;</li>
 *   <li>non-terminal past the budget — returns the latest snapshot with
 *       {@code timed_out_waiting: true}.</li>
 * </ul>
 */
class TaskStatusWaitTest {

    private static String snapshot(boolean terminal) {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        JsonObject data = new JsonObject();
        data.addProperty("state", terminal ? "success" : "running");
        data.addProperty("terminal", terminal);
        r.add("data", data);
        return r.toString();
    }

    @Test
    void zeroWaitReturnsSingleSnapshotWithoutBlocking() {
        AtomicInteger calls = new AtomicInteger();
        String result = McpServer.waitForTerminal(() -> {
            calls.incrementAndGet();
            return snapshot(false);
        }, 0);

        assertEquals(1, calls.get(), "wait_seconds=0 must poll exactly once");
        JsonObject parsed = JsonParser.parseString(result).getAsJsonObject();
        assertFalse(parsed.has("timed_out_waiting"),
                "no wait budget means no timed_out_waiting flag");
    }

    @Test
    void terminalSnapshotReturnsImmediatelyEvenWithWaitBudget() {
        AtomicInteger calls = new AtomicInteger();
        String result = McpServer.waitForTerminal(() -> {
            calls.incrementAndGet();
            return snapshot(true);
        }, 5);

        assertEquals(1, calls.get(), "a terminal snapshot must not keep polling");
        JsonObject parsed = JsonParser.parseString(result).getAsJsonObject();
        assertTrue(parsed.getAsJsonObject("data").get("terminal").getAsBoolean());
        assertFalse(parsed.has("timed_out_waiting"));
    }

    @Test
    void nonTerminalPastBudgetReturnsLatestSnapshotWithTimedOutWaiting() {
        AtomicInteger calls = new AtomicInteger();
        String result = McpServer.waitForTerminal(() -> {
            calls.incrementAndGet();
            return snapshot(false);
        }, 1);

        assertTrue(calls.get() >= 1, "the snapshot supplier must be polled at least once");
        JsonObject parsed = JsonParser.parseString(result).getAsJsonObject();
        assertTrue(parsed.get("timed_out_waiting").getAsBoolean(),
                "a non-terminal snapshot past the budget must carry timed_out_waiting=true");
        // The latest snapshot's own fields are preserved.
        assertEquals("running", parsed.getAsJsonObject("data").get("state").getAsString());
    }

    @Test
    void resumesAcrossCallsOnceTheTaskActuallyFinishes() {
        AtomicInteger calls = new AtomicInteger();
        // First call: wait budget exhausted while still running → timed_out_waiting.
        String first = McpServer.waitForTerminal(() -> {
            calls.incrementAndGet();
            return snapshot(false);
        }, 1);
        assertTrue(JsonParser.parseString(first).getAsJsonObject()
                .get("timed_out_waiting").getAsBoolean());

        // Second call with the same task_id: the task is now terminal → immediate return.
        String second = McpServer.waitForTerminal(() -> {
            calls.incrementAndGet();
            return snapshot(true);
        }, 5);
        JsonObject parsed = JsonParser.parseString(second).getAsJsonObject();
        assertTrue(parsed.getAsJsonObject("data").get("terminal").getAsBoolean());
        assertFalse(parsed.has("timed_out_waiting"));
    }
}
