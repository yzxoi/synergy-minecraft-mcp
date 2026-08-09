package com.dwinovo.numen.core.task;

import com.dwinovo.numen.core.task.combat.CombatStance;
import com.dwinovo.numen.task.TaskRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed descriptor for a bounded tactical combat episode. */
public final class CombatTaskRecord extends TaskRecord {
    public static final String TOOL_NAME = "combat";

    public final List<Integer> entityIds;
    public final CombatStance stance;
    public final double maxRange;
    public final double fleeHealth;

    private final Set<Integer> defeated = new LinkedHashSet<>();
    private final Set<Integer> lost = new LinkedHashSet<>();
    private final Set<Integer> unreachable = new LinkedHashSet<>();
    private final Map<Integer, Integer> hits = new LinkedHashMap<>();

    public CombatTaskRecord(String toolCallId, long deadlineGameTime, List<Integer> entityIds,
                            CombatStance stance, double maxRange, double fleeHealth) {
        super(TOOL_NAME, toolCallId, deadlineGameTime);
        this.entityIds = List.copyOf(entityIds);
        this.stance = stance;
        this.maxRange = maxRange;
        this.fleeHealth = fleeHealth;
        setStallWarningTicks(20L * 15L);
    }

    public void defeated(int id) {
        if (!terminal(id)) defeated.add(id);
    }
    public void lost(int id) {
        if (!terminal(id)) lost.add(id);
    }
    public void unreachable(int id) {
        if (!terminal(id)) unreachable.add(id);
    }
    public void hit(int id) { hits.merge(id, 1, Integer::sum); }
    public Set<Integer> defeated() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(defeated));
    }
    public Set<Integer> lost() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(lost));
    }
    public Set<Integer> unreachable() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unreachable));
    }
    public Map<Integer, Integer> hits() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(hits));
    }
    public boolean terminal(int id) {
        return defeated.contains(id) || lost.contains(id) || unreachable.contains(id);
    }

    @Override public String describe() {
        return TOOL_NAME + " " + stance.wireName() + " " + defeated.size() + "/" + entityIds.size();
    }
}
