package com.dwinovo.numen.core.task.combat;

/** Minecraft-free body facts consumed by the tactical kernel. */
public record BodyDatum(double health, boolean armed, boolean inLava, int activeThreats) {}
