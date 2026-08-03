package com.dwinovo.numen.core.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Source metadata used to create NeoForge 1.21.11 registry-backed game tests.
 * This is deliberately local metadata, not the removed Minecraft annotation API.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    String template();

    int timeoutTicks();

    String batch();

    /** False while a test still depends on a fixture absent from this repository. */
    boolean migrated() default true;
}
