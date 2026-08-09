package com.dwinovo.numen.core.task.combat;

import java.util.Locale;

/** High-level combat intent selected by the semantic planner. */
public enum CombatStance {
    DEFENSIVE,
    AGGRESSIVE,
    KITE;

    public static CombatStance parse(String value) {
        if (value == null || value.isBlank()) return DEFENSIVE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException(
                    "stance must be one of defensive, aggressive, kite");
        }
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
