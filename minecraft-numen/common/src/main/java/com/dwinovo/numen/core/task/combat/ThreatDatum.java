package com.dwinovo.numen.core.task.combat;

/** Immutable, transport-safe threat observation with no Minecraft object references. */
public record ThreatDatum(
        int entityId,
        String type,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        double distance,
        double bearing,
        boolean lineOfSight,
        long lastSeenGameTime,
        boolean hurtByRecency,
        boolean attackingUs,
        boolean rangedAttacker,
        double health,
        boolean removed) {

    public double speed() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }
}
