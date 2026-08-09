package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.agent.tool.api.ToolContext;
import com.dwinovo.numen.core.task.CombatTaskRecord;
import com.dwinovo.numen.core.task.combat.CombatStance;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatToolsTest {

    @Test
    void normalizesRuntimeIdsWithoutChangingTheirOrder() {
        assertEquals(List.of(184, 207, 215),
                CombatTools.normalizeEntityIds(List.of(184, 207, 184, 215)));
    }

    @Test
    void rejectsAnEmptyAuthorizedSet() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatTools.normalizeEntityIds(List.of()));
    }

    @Test
    void rejectsMoreThanTwentyDistinctTargets() {
        List<Integer> ids = new ArrayList<>();
        for (int id = 1; id <= 21; id++) ids.add(id);
        assertThrows(IllegalArgumentException.class,
                () -> CombatTools.normalizeEntityIds(ids));
    }

    @Test
    void acceptsInclusiveCombatBoundsAndBuildsDeadlineFromGameTime() {
        CombatTools tools = new CombatTools();
        ToolContext ctx = new ToolContext("call-1", 100L);

        CombatTaskRecord minimum = (CombatTaskRecord) tools.combat(List.of(7),
                CombatStance.DEFENSIVE, 4.0, 4.0, 30, ctx);
        CombatTaskRecord maximum = (CombatTaskRecord) tools.combat(List.of(7),
                CombatStance.KITE, 32.0, 16.0, 600, ctx);

        assertEquals(700L, minimum.getDeadlineGameTime());
        assertEquals(12_100L, maximum.getDeadlineGameTime());
    }

    @Test
    void rejectsValuesOutsideEveryCombatBound() {
        CombatTools tools = new CombatTools();
        ToolContext ctx = new ToolContext("call-2", 0L);

        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 3.9, 8.0, 300, ctx));
        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 32.1, 8.0, 300, ctx));
        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 12.0, 3.9, 300, ctx));
        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 12.0, 16.1, 300, ctx));
        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 12.0, 8.0, 29, ctx));
        assertThrows(IllegalArgumentException.class,
                () -> tools.combat(List.of(7), CombatStance.DEFENSIVE, 12.0, 8.0, 601, ctx));
    }

    @Test
    void invalidStanceReturnsStructuredValidationWithoutTouchingTheBody() {
        JsonObject args = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(7);
        args.add("entity_ids", ids);
        args.addProperty("stance", "berserk");
        AtomicReference<String> reply = new AtomicReference<>();

        new CombatTool().onServerCall("call-3", args, null, reply::set);

        JsonObject result = JsonParser.parseString(reply.get()).getAsJsonObject();
        assertFalse(result.get("success").getAsBoolean());
        assertEquals("validation", result.get("code").getAsString());
        assertFalse(result.get("retryable").getAsBoolean());
    }

    @Test
    void malformedCombatPayloadReturnsStructuredValidation() {
        JsonObject args = new JsonObject();
        args.addProperty("entity_ids", "not-an-array");
        AtomicReference<String> reply = new AtomicReference<>();

        new CombatTool().onServerCall("call-4", args, null, reply::set);

        JsonObject result = JsonParser.parseString(reply.get()).getAsJsonObject();
        assertFalse(result.get("success").getAsBoolean());
        assertEquals("validation", result.get("code").getAsString());
        assertFalse(result.get("retryable").getAsBoolean());
    }

}
