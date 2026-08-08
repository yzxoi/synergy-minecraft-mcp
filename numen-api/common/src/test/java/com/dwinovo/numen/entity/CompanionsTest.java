package com.dwinovo.numen.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the companion death-state predicate and the Entry
 * lifecycle transitions. No Minecraft server dependency — uses only Java
 * primitives and avoids net.minecraft static initializers.
 */
class CompanionsTest {

    // -- awaitingRespawn predicate (pure boolean over diedAt) ------------

    @Test
    void awaitingRespawnIsFalseForNullEntry() {
        assertFalse(Companions.awaitingRespawn(null));
    }

    @Test
    void awaitingRespawnIsFalseForZeroDiedAt() {
        assertFalse(isAwaitingRespawn(0L));
    }

    @Test
    void awaitingRespawnIsTrueForPositiveDiedAt() {
        assertTrue(isAwaitingRespawn(12345L));
        assertTrue(isAwaitingRespawn(1L));
        assertTrue(isAwaitingRespawn(Long.MAX_VALUE));
    }

    @Test
    void awaitingRespawnDistinguishesLiveFromDeadWindows() {
        assertFalse(isAwaitingRespawn(0L), "live → not awaiting");
        assertTrue(isAwaitingRespawn(500L), "dead → awaiting");
        assertFalse(isAwaitingRespawn(0L), "revived back to 0 → not awaiting");
    }

    // -- Entry lifecycle transitions (null-safe construction) ------------

    private static CompanionRegistry.Entry entry(String name) {
        return new CompanionRegistry.Entry(name, java.util.UUID.randomUUID(),
                null, null, "", 0L, "", "");
    }

    @Test
    void deadTransitionRecordsCauseAndTimestamp() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        var e = new CompanionRegistry.Entry("sama", owner, null, null, "", 0L, "", "");
        var dead = e.dead("lava", 7200L);

        org.junit.jupiter.api.Assertions.assertEquals("lava", dead.deathCause());
        org.junit.jupiter.api.Assertions.assertEquals(7200L, dead.diedAt());
        org.junit.jupiter.api.Assertions.assertEquals("sama", dead.name(), "name preserved");
        org.junit.jupiter.api.Assertions.assertEquals(owner, dead.owner(), "owner preserved");
    }

    @Test
    void aliveTransitionClearsDeathState() {
        var e = entry("sama");
        var dead = e.dead("arrow", 100L);
        var alive = dead.alive();

        org.junit.jupiter.api.Assertions.assertEquals(0L, alive.diedAt());
        org.junit.jupiter.api.Assertions.assertEquals("", alive.deathCause());
    }

    @Test
    void aliveTransitionOnLiveEntryIsIdempotent() {
        var e = entry("sama");
        org.junit.jupiter.api.Assertions.assertEquals(0L, e.diedAt());
        var stillAlive = e.alive();
        org.junit.jupiter.api.Assertions.assertEquals(0L, stillAlive.diedAt());
    }

    @Test
    void movedToPreservesDeathState() {
        var e = entry("sama");
        var dead = e.dead("cactus", 500L);
        var moved = dead.movedTo(null, null);

        org.junit.jupiter.api.Assertions.assertEquals(500L, moved.diedAt(),
                "death state must survive move");
        org.junit.jupiter.api.Assertions.assertEquals("cactus", moved.deathCause(),
                "death cause must survive move");
    }

    @Test
    void withSkinPreservesDeathState() {
        var e = entry("sama");
        var dead = e.dead("fall", 300L);
        var skinned = dead.withSkin("base64value", "base64sig");

        org.junit.jupiter.api.Assertions.assertEquals("base64value", skinned.skinValue());
        org.junit.jupiter.api.Assertions.assertEquals("base64sig", skinned.skinSig());
        org.junit.jupiter.api.Assertions.assertEquals(300L, skinned.diedAt(),
                "death state must survive skin change");
        org.junit.jupiter.api.Assertions.assertEquals("fall", skinned.deathCause(),
                "death cause must survive skin change");
    }

    // -- Pure decision seam: the invariant exposed for testing ----------

    static boolean isAwaitingRespawn(long diedAt) {
        return Companions.awaitingRespawn(
                new CompanionRegistry.Entry("_", java.util.UUID.randomUUID(),
                        null, null, "_", diedAt, "", ""));
    }
}