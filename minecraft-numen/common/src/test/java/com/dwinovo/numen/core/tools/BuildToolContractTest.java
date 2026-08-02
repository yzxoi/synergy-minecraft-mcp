package com.dwinovo.numen.core.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Contract tests for the public build MCP payload, independent of a live world. */
class BuildToolContractTest {

    @Test
    void schemaAdvertisesOnlyTheOrderedOpsPayload() {
        JsonObject schema = new Gson().toJsonTree(new BuildTool().parameterSchema()).getAsJsonObject();
        JsonObject properties = schema.getAsJsonObject("properties");
        JsonObject ops = properties.getAsJsonObject("ops");

        assertEquals("object", schema.get("type").getAsString());
        assertEquals("array", ops.get("type").getAsString());
        assertEquals(1, ops.get("minItems").getAsInt());
        assertEquals(1, schema.getAsJsonArray("required").size());
        assertEquals("ops", schema.getAsJsonArray("required").get(0).getAsString());
        assertFalse(properties.has("blocks"), "the retired blocks payload must not be advertised");
        assertFalse(schema.get("additionalProperties").getAsBoolean(),
                "the retired blocks payload must be rejected by the schema");
    }

    @Test
    void emptySetHelperDoesNotReportTheRetiredBlocksProtocol() throws Exception {
        Method parseTargets = BuildTool.class.getDeclaredMethod("parseTargets", java.util.List.class);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> parseTargets.invoke(null, java.util.List.of()));

        assertEquals("build set op must contain at least one cell", thrown.getCause().getMessage());
    }
}
