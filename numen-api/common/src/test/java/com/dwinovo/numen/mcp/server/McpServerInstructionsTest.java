package com.dwinovo.numen.mcp.server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the initialize guidance against advertising tools that are not in the live MCP surface. */
class McpServerInstructionsTest {

    @Test
    void initializeInstructionsUseCurrentToolNamesAndDeferToToolsList() throws Exception {
        Field field = McpServer.class.getDeclaredField("INSTRUCTIONS");
        field.setAccessible(true);
        String instructions = (String) field.get(null);

        assertTrue(instructions.contains("tools/list"));
        assertTrue(instructions.contains("goto"));
        assertTrue(instructions.contains("mine"));
        assertTrue(instructions.contains("build"));

        // These names belonged to an older tool surface and must never be suggested by initialize.
        assertFalse(instructions.contains("move_to"));
        assertFalse(instructions.contains("auto_mine"));
        assertFalse(instructions.contains("place_block"));
        assertFalse(instructions.contains("hunt"));
    }

    @Test
    void externalAsyncDescriptionsDefineTheMcpPollingContract() {
        for (String name : List.of("goto", "mine", "build", "blueprint", "collect_items",
                "fish", "melee_attack", "ranged_attack")) {
            String description = McpServer.descriptionForMcp(new FakeTool(name,
                    "wait for task_finished; status=done; do not poll; while <current_task> exists; use break_block"));
            assertTrue(description.startsWith("MCP external driver:"), name);
            assertTrue(description.contains("task_status"), name);
            assertTrue(description.contains("data.terminal=true"), name);
            assertTrue(description.contains("do not receive task_finished"), name);
            String engineDetails = description.substring(description.indexOf("Engine details"));
            assertTrue(engineDetails.contains("built-in brain completion event"), name);
            assertTrue(engineDetails.contains("built-in brain done status"), name);
            assertTrue(engineDetails.contains("built-in brain no-poll rule"), name);
            assertTrue(engineDetails.contains("built-in brain current-task marker"), name);
            assertTrue(engineDetails.contains("interact_at(button=\"left\", x,y,z)"), name);
            assertFalse(engineDetails.contains("task_finished"), name);
            assertFalse(engineDetails.contains("status=done"), name);
            assertFalse(engineDetails.contains("do not poll"), name);
            assertFalse(engineDetails.contains("<current_task>"), name);
            assertFalse(engineDetails.contains("break_block"), name);
        }
    }

    @Test
    void nonAsyncDescriptionsRemainByteForByteUnchanged() {
        String original = "task_finished status=done do not poll <current_task>";
        assertTrue(McpServer.descriptionForMcp(new FakeTool("task_status", original))
                .equals(original));
    }

    @Test
    void accessPromptUsesLiveToolNames() throws Exception {
        String prompt = McpAccessPrompt.build("http://127.0.0.1:8765/mcp", "token");
        assertTrue(prompt.contains("`goto`, `mine`"));
        assertTrue(prompt.contains("`data.terminal=true`"));
        assertFalse(prompt.contains("move_to"));
        assertFalse(prompt.contains("auto_mine"));
    }

    private record FakeTool(String name, String description) implements com.dwinovo.numen.agent.tool.NumenTool {
        @Override public Map<String, Object> parameterSchema() { return Map.of("type", "object"); }
        @Override public void onServerCall(String id, com.google.gson.JsonObject args,
                                           com.dwinovo.numen.entity.NumenPlayer companion,
                                           java.util.function.Consumer<String> reply) {}
    }
}
