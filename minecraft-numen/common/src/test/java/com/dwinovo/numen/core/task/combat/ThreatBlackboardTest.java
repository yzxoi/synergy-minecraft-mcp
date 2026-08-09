package com.dwinovo.numen.core.task.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatBlackboardTest {

    private static ThreatDatum threat(int id, double distance, boolean hurtBy) {
        return new ThreatDatum(id, "zombie", id, 64, 0, 0, 0, 0,
                distance, 0, true, 100, hurtBy, true, false, 20, false);
    }

    @Test
    void capacityAndTieBreakingAreDeterministic() {
        ThreatBlackboard board = new ThreatBlackboard();
        List<ThreatDatum> observed = new ArrayList<>();
        for (int id = 20; id >= 1; id--) observed.add(threat(id, 5, false));
        board.update(observed, 100);

        assertEquals(ThreatBlackboard.MAX_THREATS, board.threats().size());
        assertEquals(1, board.threats().getFirst().entityId());
        assertEquals(16, board.threats().getLast().entityId());
    }

    @Test
    void recentAttackerWinsDistanceTie() {
        ThreatBlackboard board = new ThreatBlackboard();
        board.update(List.of(threat(3, 5, false), threat(9, 5, true)), 100);
        assertEquals(9, board.threats().getFirst().entityId());
    }

    @Test
    void entriesExpireAfterFortyTicksNotAtTheBoundary() {
        ThreatBlackboard board = new ThreatBlackboard();
        board.update(List.of(threat(3, 5, false)), 100);
        board.expire(140);
        assertEquals(1, board.threats().size());
        board.expire(141);
        assertTrue(board.threats().isEmpty());
    }

    @Test
    void snapshotIsImmutableAndPreservesAllObservedFields() {
        ThreatBlackboard board = new ThreatBlackboard();
        ThreatDatum original = new ThreatDatum(3, "skeleton", 1, 2, 3, 4, 5, 6,
                7, 8, true, 9, true, true, true, 10, false);
        board.update(List.of(original), 9);
        assertEquals(original, board.threats().getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> board.threats().add(threat(4, 1, false)));
    }

    @Test
    void velocityAndBearingMathUseMinecraftTickRateAndNormalizedAngles() {
        assertEquals(4.0, TacticalDecisions.velocity(1, 3, 10), 1.0e-9);
        assertEquals(-180.0, TacticalDecisions.bearing(0, -1, 0), 1.0e-9);
        assertEquals(-90.0, TacticalDecisions.bearing(1, 0, 0), 1.0e-9);
    }
}
