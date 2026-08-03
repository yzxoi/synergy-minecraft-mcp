package com.dwinovo.numen.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for task_status, including retained terminal outcomes. */
class TaskStatusToolTest {

    private static final class TestRecord extends TaskRecord {
        TestRecord(String toolCallId) {
            super("test_action", toolCallId, 2_000);
        }

        @Override
        public String describe() {
            return "test action";
        }
    }

    @Test
    void retainedSuccessRemainsTerminalAfterTheOutboxIsDrained() {
        TestRecord record = new TestRecord("mcp-success");
        record.markStarted(100);
        record.setState(TaskState.SUCCESS);
        record.setResult(TaskResult.ok("placed one block", Map.of("placed", 1)));

        JsonObject json = parse(TaskStatusTool.resultFor(record, record.publicId(), 120));
        JsonObject snapshot = json.getAsJsonObject("data");
        assertEquals("success", snapshot.get("state").getAsString());
        assertTrue(snapshot.get("terminal").getAsBoolean());
        assertEquals("placed one block",
                snapshot.getAsJsonObject("result").get("message").getAsString());
    }

    @Test
    void retainedFailurePreservesFailureResultAndTerminalState() {
        TestRecord record = new TestRecord("mcp-failure");
        record.markStarted(100);
        record.setState(TaskState.FAILED);
        record.setResult(TaskResult.fail("no path", Map.of("failure_code", "no_path")));

        JsonObject snapshot = parse(TaskStatusTool.resultFor(record, record.publicId(), 140))
                .getAsJsonObject("data");
        assertEquals("failed", snapshot.get("state").getAsString());
        assertTrue(snapshot.get("terminal").getAsBoolean());
        assertEquals("no path", snapshot.getAsJsonObject("result").get("message").getAsString());
        assertEquals("no_path",
                snapshot.getAsJsonObject("result").getAsJsonObject("data")
                        .get("failure_code").getAsString());
    }

    @Test
    void retainedCancellationIsNotReportedAsIdle() {
        TestRecord record = new TestRecord("mcp-stopped");
        record.markStarted(100);
        record.setState(TaskState.CANCELLED);
        record.setResult(TaskResult.cancelled("stopped by task_stop"));

        JsonObject snapshot = parse(TaskStatusTool.resultFor(record, record.publicId(), 150))
                .getAsJsonObject("data");
        assertEquals("cancelled", snapshot.get("state").getAsString());
        assertTrue(snapshot.get("terminal").getAsBoolean());
        assertTrue(snapshot.getAsJsonObject("result").get("interrupted").getAsBoolean());
    }

    @Test
    void missingExplicitTaskUsesIdleAndUnknownStatesDistinctly() {
        JsonObject idle = parse(TaskStatusTool.resultFor(null, "", 0));
        JsonObject idleData = idle.getAsJsonObject("data");
        assertEquals("idle", idleData.get("state").getAsString());
        assertFalse(idle.has("terminal"));

        JsonObject unknown = parse(TaskStatusTool.resultFor(null, "t404", 0));
        assertEquals("unknown", unknown.getAsJsonObject("data").get("state").getAsString());
        assertEquals("t404", unknown.getAsJsonObject("data").get("task_id").getAsString());
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
