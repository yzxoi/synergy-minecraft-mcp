package com.dwinovo.numen.core.task.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded, deterministic threat memory shared by observation and decision code. */
public final class ThreatBlackboard {
    public static final int MAX_THREATS = 16;
    public static final long TTL_TICKS = 40;

    private static final Comparator<ThreatDatum> ORDER = Comparator
            .comparingDouble(TacticalDecisions::threatScore)
            .thenComparingInt(ThreatDatum::entityId);

    private final Map<Integer, ThreatDatum> entries = new HashMap<>();

    public void update(List<ThreatDatum> observed, long gameTime) {
        for (ThreatDatum threat : observed) entries.put(threat.entityId(), threat);
        expire(gameTime);
        trim();
    }

    public void expire(long gameTime) {
        entries.values().removeIf(t -> gameTime - t.lastSeenGameTime() > TTL_TICKS);
    }

    public List<ThreatDatum> threats() {
        return entries.values().stream().sorted(ORDER).toList();
    }

    public void clear() {
        entries.clear();
    }

    private void trim() {
        List<ThreatDatum> sorted = new ArrayList<>(threats());
        if (sorted.size() <= MAX_THREATS) return;
        entries.clear();
        for (int i = 0; i < MAX_THREATS; i++) {
            ThreatDatum threat = sorted.get(i);
            entries.put(threat.entityId(), threat);
        }
    }
}
