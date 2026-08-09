package com.dwinovo.numen.core.task.combat;

import java.util.Locale;

/** Fast tactical mode chosen from the current body and threat snapshot. */
public enum CombatTactic {
    DISENGAGE,
    CHASE,
    STRAFE,
    ATTACK,
    KITE,
    FLEE;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
