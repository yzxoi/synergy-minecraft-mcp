package com.dwinovo.numen.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure contract tests for task progress, terminal retention, and JSON shape. */
class TaskObservabilityTest {

    private static final class TestRecord extends TaskRecord {
        TestRecord(long deadline) {
            super("test_action", "mcp-test", deadline);
        }

        @Override
        public String describe() {
            return "test action";
        }
    }

    @Test
    void activityDoesNotPretendThatMaterialProgressAdvanced() {
        TestRecord r = new TestRecord(2_000);
        r.markStarted(100);
        r.reportProgress(120, "navigating", "moved", Map.of("remaining", 9));
        r.reportActivity(180, "navigating", "replanning", Map.of("remaining", 9));

        TaskProgress p = r.getProgress();
        assertEquals(180, p.updatedGameTime());
        assertEquals(120, p.advancedGameTime());
        assertEquals("replanning", p.message());
    }

    @Test
    void snapshotSignalsStaleProgressWithoutForcingATerminalState() {
        TestRecord r = new TestRecord(2_000);
        r.markStarted(100);
        r.setState(TaskState.RUNNING);
        r.setStallWarningTicks(200);
        r.reportProgress(120, "mining", "one item", Map.of("gathered", 1));
        r.reportActivity(350, "mining", "still attempting", Map.of("gathered", 1));

        Map<String, Object> data = TaskSnapshot.capture(r, 350).toData();
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) data.get("progress");
        assertEquals(true, progress.get("stalled"));
        assertEquals(11L, progress.get("seconds_since_progress"));
        assertEquals("running", data.get("state"));
        assertEquals(false, data.get("terminal"));
    }

    @Test
    void taskResultPreservesNestedStructuredValues() {
        TaskResult result = TaskResult.ok("snapshot", Map.of(
                "progress", Map.of("phase", "building", "completed", 3),
                "candidates", List.of("a", "b")));

        JsonObject json = JsonParser.parseString(result.toJson()).getAsJsonObject();
        JsonObject data = json.getAsJsonObject("data");
        assertTrue(data.get("progress").isJsonObject());
        assertEquals(3, data.getAsJsonObject("progress").get("completed").getAsInt());
        assertTrue(data.get("candidates").isJsonArray());
    }

    @Test
    void underAttackKeepsAOneHundredTickDamageMemory() {
        assertTrue(BodySituation.underAttack(200, 100, true, 0, 0));
        assertFalse(BodySituation.underAttack(201, 100, true, 0, 0));
        assertFalse(BodySituation.underAttack(200, 100, false, 0, 0));
    }

    @Test
    void activeThreatSignalsOverrideExpiredDamageMemory() {
        assertTrue(BodySituation.underAttack(500, 100, true, 1, 0));
        assertTrue(BodySituation.underAttack(500, 100, true, 0, 1));
    }

    @Test
    void completedTaskRemainsAddressableAfterOutboxDrain() {
        TaskQueue q = new TaskQueue();
        TestRecord r = new TestRecord(1_000);
        r.markStarted(10);
        r.setState(TaskState.SUCCESS);
        r.setResult(TaskResult.ok("done", Map.of("count", 1)));

        q.complete(r);
        assertEquals(List.of(r), q.drainCompleted());
        assertTrue(q.drainCompleted().isEmpty());
        assertEquals(r, q.findRecent(r.publicId()));
    }

    @Test
    void terminalHistoryIsBounded() {
        TaskQueue q = new TaskQueue();
        TestRecord first = null;
        TestRecord last = null;
        for (int i = 0; i < 33; i++) {
            TestRecord r = new TestRecord(1_000);
            r.setState(TaskState.SUCCESS);
            r.setResult(TaskResult.ok("done"));
            q.complete(r);
            if (i == 0) first = r;
            last = r;
        }

        assertNull(q.findRecent(first.publicId()));
        assertEquals(last, q.findRecent(last.publicId()));
        assertFalse(q.drainCompleted().isEmpty());
    }
}
