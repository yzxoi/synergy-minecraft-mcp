package com.dwinovo.numen.core.task.combat;

import java.util.List;

/** The only tactical intent allowed to reach the motor layer. */
public record ShieldedIntent(TacticalIntent intent, List<String> filtersApplied) {
    public ShieldedIntent {
        filtersApplied = List.copyOf(filtersApplied);
    }
}
