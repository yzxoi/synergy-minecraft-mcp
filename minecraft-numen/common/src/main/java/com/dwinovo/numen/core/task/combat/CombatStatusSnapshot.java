package com.dwinovo.numen.core.task.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable projection of the reactive combat layer for tools and diagnostics. */
public record CombatStatusSnapshot(
        boolean active,
        String source,
        String stance,
        String tactic,
        Integer targetEntityId,
        double health,
        double maxRange,
        double fleeHealth,
        List<ThreatDatum> threats,
        List<String> safetyFilters,
        long gameTime) {

    public CombatStatusSnapshot {
        threats = threats == null ? List.of() : List.copyOf(threats);
        safetyFilters = safetyFilters == null ? List.of() : List.copyOf(safetyFilters);
    }

    public static CombatStatusSnapshot idle(double health, long gameTime) {
        return new CombatStatusSnapshot(false, "idle", CombatStance.DEFENSIVE.wireName(),
                CombatTactic.DISENGAGE.name().toLowerCase(java.util.Locale.ROOT), null,
                health, 0.0, 0.0, List.of(), List.of(), gameTime);
    }

    /** Gson-friendly stable wire shape. */
    public Map<String, Object> toData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", active);
        out.put("source", source);
        out.put("stance", stance);
        out.put("tactic", tactic);
        if (targetEntityId != null) out.put("target_entity_id", targetEntityId);
        out.put("health", health);
        out.put("max_range", maxRange);
        out.put("flee_health", fleeHealth);
        out.put("safety_filters", safetyFilters);
        out.put("game_time", gameTime);
        List<Map<String, Object>> threatData = new ArrayList<>();
        for (ThreatDatum threat : threats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entity_id", threat.entityId());
            item.put("type", threat.type());
            item.put("distance", threat.distance());
            item.put("bearing", threat.bearing());
            item.put("speed", threat.speed());
            item.put("line_of_sight", threat.lineOfSight());
            item.put("attacking_us", threat.attackingUs());
            item.put("ranged_attacker", threat.rangedAttacker());
            item.put("health", threat.health());
            item.put("last_seen_game_time", threat.lastSeenGameTime());
            threatData.add(item);
        }
        out.put("threats", threatData);
        return out;
    }
}
