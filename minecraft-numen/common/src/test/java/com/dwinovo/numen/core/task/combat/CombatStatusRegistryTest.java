package com.dwinovo.numen.core.task.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CombatStatusRegistryTest {

    @Test
    void lifecycleClearRemovesTheLatestBodySnapshot() {
        UUID bodyId = UUID.randomUUID();
        CombatStatusSnapshot snapshot = CombatStatusSnapshot.idle(20.0, 100L);

        try {
            CombatStatusRegistry.publish(bodyId, snapshot);
            assertSame(snapshot, CombatStatusRegistry.get(bodyId));
        } finally {
            CombatStatusRegistry.clear(bodyId);
        }

        assertNull(CombatStatusRegistry.get(bodyId));
    }
}
