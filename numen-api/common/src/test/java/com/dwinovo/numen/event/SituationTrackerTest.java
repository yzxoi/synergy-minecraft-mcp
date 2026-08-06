package com.dwinovo.numen.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless contract tests for {@link SituationTracker#observe} — the pure
 * edge-detection pass over raw body observations. No Minecraft objects, so
 * the fall/water/air/damage edges are testable without a server.
 */
class SituationTrackerTest {

    @Test
    void fallFiresOnlyAfterCumulativeDropOfThreeBlocks() {
        SituationTracker tracker = new SituationTracker();
        // Grounded at y=100.
        tracker.observe(0, false, false, 20.0, 100.0, true, 300);

        // Leaves the ground: fall starts measuring from y=100.
        assertTrue(tracker.observe(1, false, false, 20.0, 100.0, false, 300).isEmpty());
        // Gravity moves ~0.1-0.2 blocks per tick — a 1-block drop must NOT fire.
        assertTrue(tracker.observe(2, false, false, 20.0, 99.0, false, 300).isEmpty());
        assertTrue(tracker.observe(3, false, false, 20.0, 97.5, false, 300).isEmpty());

        // Cumulative drop ≥ 3 blocks → FELL with the measured distance.
        List<SituationTracker.Event> events = tracker.observe(4, false, false, 20.0, 96.5, false, 300);
        assertEquals(1, events.size());
        assertEquals(GameEvents.Kind.FELL, events.get(0).kind());
        assertEquals(3.5, events.get(0).data().get("fell_blocks"));
    }

    @Test
    void fallFiresOncePerEpisodeAndResetsOnLanding() {
        SituationTracker tracker = new SituationTracker();
        tracker.observe(0, false, false, 20.0, 100.0, true, 300);
        tracker.observe(1, false, false, 20.0, 100.0, false, 300);

        // First episode: fire at ≥3 blocks, then latch (no repeat while still falling).
        assertEquals(1, tracker.observe(2, false, false, 20.0, 96.5, false, 300).size());
        assertTrue(tracker.observe(3, false, false, 20.0, 90.0, false, 300).isEmpty());

        // Land → latch resets; a second fall fires again.
        tracker.observe(4, false, false, 20.0, 90.0, true, 300);
        tracker.observe(5, false, false, 20.0, 90.0, false, 300);
        List<SituationTracker.Event> second = tracker.observe(6, false, false, 20.0, 86.0, false, 300);
        assertEquals(1, second.size());
        assertEquals(GameEvents.Kind.FELL, second.get(0).kind());
        assertEquals(4.0, second.get(0).data().get("fell_blocks"));
    }

    @Test
    void waterAndAirEdgesFireOnStateChangeOnly() {
        SituationTracker tracker = new SituationTracker();
        // Dry, full air.
        tracker.observe(0, false, false, 20.0, 100.0, true, 300);

        List<SituationTracker.Event> entered = tracker.observe(1, true, false, 20.0, 100.0, false, 280);
        assertEquals(1, entered.size());
        assertEquals(GameEvents.Kind.ENTERED_WATER, entered.get(0).kind());

        // Still in water: no repeat enter event.
        assertTrue(tracker.observe(2, true, false, 20.0, 100.0, false, 270).isEmpty());

        List<SituationTracker.Event> left = tracker.observe(3, false, false, 20.0, 100.0, true, 300);
        assertEquals(1, left.size());
        assertEquals(GameEvents.Kind.LEFT_WATER, left.get(0).kind());

        // Air drops to low → AIR_LOW with the air value.
        List<SituationTracker.Event> low = tracker.observe(4, false, true, 20.0, 100.0, true, 85);
        assertEquals(1, low.size());
        assertEquals(GameEvents.Kind.AIR_LOW, low.get(0).kind());
        assertEquals(85, low.get(0).data().get("air"));
    }

    @Test
    void damageFiresOnlyOnHpLossOfAtLeastTwoHearts() {
        SituationTracker tracker = new SituationTracker();
        tracker.observe(0, false, false, 20.0, 100.0, true, 300);

        // Small chip: below threshold, no event.
        assertTrue(tracker.observe(1, false, false, 19.0, 100.0, true, 300).isEmpty());
        // ≥ 2 hearts down from the last observed value → DAMAGED.
        List<SituationTracker.Event> damaged = tracker.observe(2, false, false, 16.0, 100.0, true, 300);
        assertEquals(1, damaged.size());
        assertEquals(GameEvents.Kind.DAMAGED, damaged.get(0).kind());
        assertEquals(19.0, damaged.get(0).data().get("from_hp"));
        assertEquals(16.0, damaged.get(0).data().get("to_hp"));
        // Healing is not damage.
        assertTrue(tracker.observe(3, false, false, 18.0, 100.0, true, 300).isEmpty());
    }
}
