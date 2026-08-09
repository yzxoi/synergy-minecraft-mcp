package com.dwinovo.numen.core.task.combat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Latest combat snapshot per body; lifecycle hooks clear entries on death/removal. */
public final class CombatStatusRegistry {
    private static final ConcurrentMap<UUID, CombatStatusSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private CombatStatusRegistry() {}

    public static void publish(UUID bodyId, CombatStatusSnapshot snapshot) {
        SNAPSHOTS.put(bodyId, snapshot);
    }

    public static CombatStatusSnapshot get(UUID bodyId) {
        return SNAPSHOTS.get(bodyId);
    }

    public static void clear(UUID bodyId) {
        SNAPSHOTS.remove(bodyId);
    }
}
