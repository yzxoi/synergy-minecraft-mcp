package com.dwinovo.numen.task;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTerminalStoreTest {

    @Test
    void deathReceiptSurvivesLookupByCompanionAndTaskId() {
        UUID companion = UUID.randomUUID();
        TaskRecord record = new TestRecord("mcp-death-test");
        record.setState(TaskState.CANCELLED);
        record.setResult(TaskResult.cancelled("companion died: lava",
                Map.of("termination_reason", "death", "death_cause", "lava",
                        "companion_alive", false)));

        TaskTerminalStore.remember(companion, record, 1234L);

        String json = TaskTerminalStore.statusJson(companion, record.publicId());
        var data = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("data");
        assertEquals("cancelled", data.get("state").getAsString());
        assertTrue(data.get("terminal").getAsBoolean());
        assertEquals("death", data.getAsJsonObject("result")
                .getAsJsonObject("data").get("termination_reason").getAsString());
    }

    @Test
    void deathReceiptCanAlsoBeQueriedByDisplayName() {
        UUID companion = UUID.randomUUID();
        TaskRecord record = new TestRecord("mcp-name-test");
        record.setState(TaskState.CANCELLED);
        record.setResult(TaskResult.cancelled("companion died", Map.of("termination_reason", "death")));

        TaskTerminalStore.remember(companion, "sama", record, 1234L);

        String json = TaskTerminalStore.statusJsonByName("SAMA", record.publicId());
        assertTrue(json.contains("\"terminal\":true"));
    }

    private static final class TestRecord extends TaskRecord {
        TestRecord(String callId) {
            super("goto", callId, 10_000L);
        }
    }
}
