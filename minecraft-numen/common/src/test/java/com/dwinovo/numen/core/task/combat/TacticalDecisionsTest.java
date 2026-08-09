package com.dwinovo.numen.core.task.combat;

import com.dwinovo.numen.core.pathing.exec.PlayerNav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalDecisionsTest {

    private static ThreatDatum threat(int id, double distance, boolean attackingUs,
                                       boolean ranged, boolean removed) {
        return new ThreatDatum(id, "zombie", 2, 64, 0, 0, 0, 0,
                distance, 0, true, 100, false, attackingUs, ranged, 20, removed);
    }

    @Test
    void defensiveOnlyQualifiesActualAttackersWhileAggressiveIsExplicitAuthorization() {
        assertFalse(TacticalDecisions.qualifies(false, false, CombatStance.DEFENSIVE));
        assertTrue(TacticalDecisions.qualifies(true, false, CombatStance.DEFENSIVE));
        assertTrue(TacticalDecisions.qualifies(false, true, CombatStance.DEFENSIVE));
        assertTrue(TacticalDecisions.qualifies(false, false, CombatStance.AGGRESSIVE));
    }

    @Test
    void lowHealthUnarmedAndMultiThreatBodiesFlee() {
        ThreatDatum target = threat(7, 4, true, false, false);
        assertEquals(CombatTactic.FLEE, TacticalDecisions.decide(
                new BodyDatum(8, true, false, 1), target, false, false,
                CombatStance.DEFENSIVE).tactic());
        assertEquals(CombatTactic.FLEE, TacticalDecisions.decide(
                new BodyDatum(20, false, false, 1), target, false, false,
                CombatStance.DEFENSIVE).tactic());
        assertEquals(CombatTactic.FLEE, TacticalDecisions.decide(
                new BodyDatum(12, true, false, 2), target, false, false,
                CombatStance.DEFENSIVE).tactic());
    }

    @Test
    void attackRequiresReachSightAndNativeCooldown() {
        ThreatDatum target = threat(7, 2.5, true, false, false);
        BodyDatum body = new BodyDatum(20, true, false, 1);
        assertEquals(CombatTactic.ATTACK, TacticalDecisions.decide(
                body, target, true, true, CombatStance.DEFENSIVE).tactic());
        assertEquals(CombatTactic.STRAFE, TacticalDecisions.decide(
                body, target, true, false, CombatStance.DEFENSIVE).tactic());
        assertEquals(CombatTactic.CHASE, TacticalDecisions.decide(
                body, target, false, true, CombatStance.DEFENSIVE).tactic());
        assertFalse(TacticalDecisions.chaseGoalReached(false, true, false));
        assertTrue(TacticalDecisions.chaseGoalReached(false, true, true));
        assertTrue(TacticalDecisions.chaseGoalReached(true, false, false));
    }

    @Test
    void kiteStanceAndRangedThreatsUseLateralRangeControl() {
        BodyDatum body = new BodyDatum(20, true, false, 1);
        assertEquals(CombatTactic.KITE, TacticalDecisions.decide(
                body, threat(9, 7, true, true, false), false, true,
                CombatStance.DEFENSIVE).tactic());
        assertEquals(CombatTactic.KITE, TacticalDecisions.decide(
                body, threat(9, 7, true, false, false), false, true,
                CombatStance.KITE).tactic());
    }

    @Test
    void safetyShieldOverridesLavaCriticalHealthAndRemovedTargets() {
        TacticalIntent attack = new TacticalIntent(CombatTactic.ATTACK, true, 7);
        ShieldedIntent lava = TacticalDecisions.shield(attack,
                new BodyDatum(20, true, true, 1), true);
        assertEquals(CombatTactic.FLEE, lava.intent().tactic());
        assertFalse(lava.intent().attack());
        assertTrue(lava.filtersApplied().contains("lava"));

        ShieldedIntent critical = TacticalDecisions.shield(attack,
                new BodyDatum(4, true, false, 1), true);
        assertEquals(CombatTactic.FLEE, critical.intent().tactic());
        assertTrue(critical.filtersApplied().contains("critical_health"));

        ShieldedIntent gone = TacticalDecisions.shield(attack,
                new BodyDatum(20, true, false, 1), false);
        assertEquals(CombatTactic.DISENGAGE, gone.intent().tactic());
        assertFalse(gone.intent().attack());
    }

    @Test
    void strafeAndDecisionReplayAreDeterministic() {
        assertEquals(2.5, TacticalDecisions.strafeOffset(5));
        assertEquals(-1, TacticalDecisions.strafeSide(0, 5));
        assertEquals(1, TacticalDecisions.strafeSide(20, 5));

        BodyDatum body = new BodyDatum(20, true, false, 1);
        ThreatDatum target = threat(5, 5, true, false, false);
        assertEquals(TacticalDecisions.decide(body, target, false, true, CombatStance.DEFENSIVE),
                TacticalDecisions.decide(body, target, false, true, CombatStance.DEFENSIVE));
    }

    @Test
    void combatPriorityPreservesSchedulerOrderingAndBreathSafety() {
        assertEquals(Float.NEGATIVE_INFINITY,
                TacticalDecisions.priority(false, true, false, 300, false, false));
        assertEquals(Float.NEGATIVE_INFINITY,
                TacticalDecisions.priority(true, false, false, 300, false, false));
        assertEquals(Float.NEGATIVE_INFINITY,
                TacticalDecisions.priority(true, true, true, 240, false, false));
        assertEquals(Float.NEGATIVE_INFINITY,
                TacticalDecisions.priority(true, true, false, 300, true, false));
        assertEquals(Float.NEGATIVE_INFINITY,
                TacticalDecisions.priority(true, true, false, 300, false, true));
        assertEquals(5.0f,
                TacticalDecisions.priority(true, true, false, 300, false, false));
    }

    @Test
    void navigationFailureCountSurvivesSearchRestartUntilCircuitBreaker() {
        int failures = CombatController.navFailureCountAfter(0, PlayerNav.Status.FAILED,
                false, true);
        failures = CombatController.navFailureCountAfter(failures, PlayerNav.Status.RUNNING,
                false, true);
        failures = CombatController.navFailureCountAfter(failures, PlayerNav.Status.FAILED,
                false, true);
        failures = CombatController.navFailureCountAfter(failures, PlayerNav.Status.RUNNING,
                false, true);
        failures = CombatController.navFailureCountAfter(failures, PlayerNav.Status.FAILED,
                false, true);

        assertEquals(3, failures);
        assertFalse(CombatController.navFailureLimitReached(2));
        assertTrue(CombatController.navFailureLimitReached(failures));
    }

    @Test
    void acceptedArrivalAndIntermediateFleeArrivalClearOldFailures() {
        assertEquals(0, CombatController.navFailureCountAfter(2, PlayerNav.Status.ARRIVED,
                true, true));
        assertEquals(0, CombatController.navFailureCountAfter(2, PlayerNav.Status.ARRIVED,
                false, false));
    }

}
