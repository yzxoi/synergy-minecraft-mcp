package com.dwinovo.numen.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Spawns and despawns companion {@link NumenPlayer} bodies through the vanilla
 * player-join path — entirely public API, no loader-specific construction needed
 * (the only fake piece is {@link FakeConnection}, which is common).
 *
 * <p>{@link net.minecraft.server.players.PlayerList#placeNewPlayer} adds the body
 * to the player list (→ chunk loading for free) and to the level, but does NOT
 * load a hand-built fake player's {@code .dat} (that path is tied to the real
 * login flow) — so {@link #spawn} restores position / inventory / owner from disk
 * explicitly afterwards.
 * {@link net.minecraft.server.players.PlayerList#remove} saves that data back and
 * removes the body — so despawn is a clean, persisted dormancy.
 */
@com.dwinovo.numen.api.Internal
public final class CompanionFactory {

    private CompanionFactory() {}

    /**
     * Bring a companion into the world. On first creation pass a {@code pos}
     * (the spawn location, e.g. beside the owner); on a respawn from dormancy
     * pass {@code null} to keep the position restored from its {@code .dat}.
     */
    public static NumenPlayer spawn(MinecraftServer server, UUID companionUuid, String name,
                                     UUID ownerUuid, ServerLevel level, Vec3 pos) {
        // 借来的正版皮肤(Mojang 签名的 textures,注册表持久化)注入档案——客户端只认
        // 签过名的皮肤数据;没有则回落原版默认皮肤(按 UUID 哈希抽取)。
        // authlib 9(1.21.9+)把 GameProfile 变成不可变 record,属性只能在构造时给全。
        CompanionRegistry.Entry reg = CompanionRegistry.get(server).find(companionUuid);
        GameProfile profile;
        if (reg != null && !reg.skinValue().isEmpty()) {
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> props =
                    com.google.common.collect.LinkedHashMultimap.create();
            props.put("textures", new com.mojang.authlib.properties.Property(
                    "textures", reg.skinValue(), reg.skinSig().isEmpty() ? null : reg.skinSig()));
            profile = new GameProfile(companionUuid, name,
                    new com.mojang.authlib.properties.PropertyMap(props));
        } else {
            profile = new GameProfile(companionUuid, name);
        }
        NumenPlayer player = new NumenPlayer(server, level, profile, ClientInformation.createDefault());
        FakeConnection connection = new FakeConnection();
        server.getPlayerList().placeNewPlayer(connection, player,
                CommonListenerCookie.createInitial(profile, false));
        // placeNewPlayer does NOT load a hand-built fake player's .dat, so restore
        // it ourselves: position, inventory, health, owner from
        // disk. Without this a respawned companion spawns at 0,0,0 with no items.
        var savedTag = loadPlayerData(server, player);
        // 假玩家没有客户端上报的模型定制:点亮全部皮肤覆盖层与披风,否则只显示单层基础皮肤。
        // 每次 spawn(首建与重生)都重设——该字节是同步实体数据、不随 .dat 存取。
        player.showAllSkinLayers();
        // First summon defaults to survival; an existing companion keeps a saved creative mode.
        // 1.21.11 stores playerGameType in the raw player data tag even though entity loading
        // itself now goes through TagValueInput.
        GameType mode = GameType.SURVIVAL;
        if (savedTag != null && savedTag.contains("playerGameType")
                && GameType.byId(savedTag.getInt("playerGameType").orElse(0)) == GameType.CREATIVE) {
            mode = GameType.CREATIVE;
        }
        player.setGameMode(mode);
        // First spawn has no .dat to restore the owner from; set it explicitly.
        if (player.getOwnerUuid() == null) {
            player.setOwnerUuid(ownerUuid);
        }
        // An explicit pos (fresh summon) overrides the restored position; a respawn
        // from dormancy passes null to keep exactly what the .dat restored.
        if (pos != null) {
            player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot(), false);
        }
        // Saved player data can contain the old body's held movement inputs and
        // horizontal momentum. A newly spawned body must begin from rest, or a
        // stale path can immediately carry it away from the respawn position.
        InputDriver.halt(player);
        player.setShiftKeyDown(false);
        player.resetFallDistance();
        return player;
    }

    /**
     * Restore a fake player's saved state from its playerdata {@code .dat}
     * ({@link net.minecraft.server.players.PlayerList#loadPlayerData} +
     * {@link net.minecraft.world.entity.Entity#load}). {@code placeNewPlayer}
     * skips this for hand-constructed players, so we invoke the same load
     * ourselves. No-op on first summon (no file yet).
     */
    private static net.minecraft.nbt.CompoundTag loadPlayerData(
            MinecraftServer server, NumenPlayer player) {
        // 1.21.9+ 拆掉了 PlayerList.load(player, reporter):改为按 NameAndId 读回原始
        // CompoundTag,再自己包一层 TagValueInput 喂给 player.load。
        var maybe = server.getPlayerList().loadPlayerData(
                new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        maybe.map(tag -> net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), tag))
                .ifPresent(player::load);
        return maybe.orElse(null);
    }

    /** Save the companion's data and remove it from the world (dormancy). */
    public static void despawn(MinecraftServer server, NumenPlayer player) {
        // Tell tool packs the body is leaving so they can finalize their own
        // per-companion work (e.g. clear a mining crack overlay) instead of leaving
        // it orphaned once the body drops out of the tick loop's player list.
        CompanionLifecycle.fireRemove(player);
        server.getPlayerList().remove(player);
    }
}
