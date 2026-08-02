package com.dwinovo.numen.mcp.server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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
}
