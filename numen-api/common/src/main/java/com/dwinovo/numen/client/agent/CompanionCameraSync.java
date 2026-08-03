package com.dwinovo.numen.client.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Keeps the vanilla spectator camera attached to a companion across death and
 * respawn. Vanilla stores a camera target as an entity instance, while a
 * respawn creates a new instance with the same UUID and name. This small
 * client-side bridge remembers the stable UUID and resolves the replacement
 * entity on subsequent client ticks.
 */
public final class CompanionCameraSync {

    /** A network respawn/entity-add can take a few client ticks to become visible. */
    private static final int MAX_RETRY_TICKS = 100;

    private static UUID pendingUuid;
    private static int retries;

    private CompanionCameraSync() {}

    /** Called by the death payload before the old entity is removed client-side. */
    public static void onDeath(UUID uuid) {
        if (uuid == null) return;
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (isCompanion(camera, uuid)) {
            remember(uuid);
            // Do not leave the user looking at a removed/frozen entity during the
            // respawn countdown. The owner is a safe temporary camera target.
            if (mc.player != null && camera != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    /** Called by the respawn payload; the replacement may not be rendered yet. */
    public static void onRespawn(UUID uuid) {
        // Only restore a camera that was actually watching this companion at
        // death time. A respawn must never hijack the owner's normal view.
        if (uuid != null && uuid.equals(pendingUuid)) remember(uuid);
    }

    /** Run once per client tick from both supported loaders. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }

        Entity camera = mc.getCameraEntity();
        if (camera != null && isKnownCompanion(camera.getUUID())) {
            UUID cameraUuid = camera.getUUID();
            if (camera.isRemoved() || ClientDeaths.isDead(cameraUuid)) {
                remember(cameraUuid);
                if (mc.player != null && camera != mc.player) {
                    mc.setCameraEntity(mc.player);
                }
            }
        }

        if (pendingUuid == null) return;
        Entity replacement = ClientNumenLookup.resolve(pendingUuid);
        if (replacement != null && !replacement.isRemoved() && !ClientDeaths.isDead(pendingUuid)) {
            mc.setCameraEntity(replacement);
            clear();
            return;
        }
        if (--retries <= 0) clear();
    }

    /** Exposed for deterministic unit tests of the UUID/identity rule. */
    static boolean isCompanion(Entity entity, UUID uuid) {
        return entity != null && uuid != null && uuid.equals(entity.getUUID());
    }

    private static boolean isKnownCompanion(UUID uuid) {
        return uuid != null && NumenRoster.instance().name(uuid) != null;
    }

    private static void remember(UUID uuid) {
        pendingUuid = uuid;
        retries = MAX_RETRY_TICKS;
    }

    private static void clear() {
        pendingUuid = null;
        retries = 0;
    }
}
