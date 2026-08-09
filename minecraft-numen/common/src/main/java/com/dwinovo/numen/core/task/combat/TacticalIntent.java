package com.dwinovo.numen.core.task.combat;

/** One tick of tactic/attack intent produced by the pure policy for motor execution. */
public record TacticalIntent(
        CombatTactic tactic,
        boolean attack,
        Integer targetEntityId) {

    public static TacticalIntent disengage() {
        return new TacticalIntent(CombatTactic.DISENGAGE, false, null);
    }

    public static TacticalIntent flee(Integer targetEntityId) {
        return new TacticalIntent(CombatTactic.FLEE, false, targetEntityId);
    }
}
