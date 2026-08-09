package com.dwinovo.numen.core.task;

import com.dwinovo.numen.core.task.combat.CombatStance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTaskRecordTest {

    @Test
    void terminalClassificationIsExclusiveAndFirstTransitionWins() {
        CombatTaskRecord record = new CombatTaskRecord("call-1", 200L,
                List.of(7, 9), CombatStance.DEFENSIVE, 12.0, 8.0);

        record.lost(7);
        record.defeated(7);
        record.unreachable(7);
        record.defeated(9);

        assertEquals(List.of(7), record.lost().stream().toList());
        assertFalse(record.defeated().contains(7));
        assertFalse(record.unreachable().contains(7));
        assertTrue(record.defeated().contains(9));
        assertTrue(record.terminal(7));
        assertTrue(record.terminal(9));
        assertEquals("combat defensive 1/2", record.describe());
    }

    @Test
    void terminalSnapshotsCannotMutateTheRecord() {
        CombatTaskRecord record = new CombatTaskRecord("call-2", 200L,
                List.of(7), CombatStance.KITE, 12.0, 8.0);
        record.unreachable(7);
        record.hit(7);

        assertThrows(UnsupportedOperationException.class, () -> record.unreachable().add(9));
        assertThrows(UnsupportedOperationException.class, () -> record.hits().put(7, 99));
        assertEquals(1, record.hits().get(7));
    }
}
