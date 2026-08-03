package com.dwinovo.numen;

import com.dwinovo.numen.agent.skill.SkillRegistry;
import com.dwinovo.numen.mcp.client.McpClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

/**
 * Client entry point. 1.21.4 still has SEPARATE mod and game event buses
 * (1.21.5 merged them), so a single {@code @EventBusSubscriber} can't carry both
 * — registration events (key mappings / GUI layers / reload listeners) are mod-bus,
 * the tick / world-render / disconnect hooks are game-bus. We register each on its
 * own bus from the mod constructor, mirroring {@link NumenMod}.
 */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class NumenNeoForgeClient {

    public NumenNeoForgeClient(IEventBus modBus) {
        // MCP client: connect to external MCP servers in config/numen/mcp_clients.json
        // and register their tools for the built-in brain. Config dir from FML (no
        // Minecraft instance needed this early).
        McpClientManager.initClient(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID));

        // MCP server: the other direction — a loopback MCP server letting an external
        // agent drive companions directly, bypassing the built-in brain. Off unless
        // enabled in config/numen/mcp_server.json.
        com.dwinovo.numen.mcp.server.NumenMcp.initClient(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());

        // 读回上次选择的 GUI 主题(config/numen/ui.json)。
        com.dwinovo.numen.client.screen.UiTheme.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("numen"));

        // Mod bus — registration events.
        modBus.addListener(NumenNeoForgeClient::registerKeyMappings);
        modBus.addListener(NumenNeoForgeClient::registerGuiLayers);
        modBus.addListener(NumenNeoForgeClient::registerReloadListeners);
        // Game bus — per-tick / world-render / disconnect.
        NeoForge.EVENT_BUS.addListener(NumenNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(NumenNeoForgeClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(NumenNeoForgeClient::onRenderLevel);
    }

    static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks event) {
        // 寻路调试覆盖层:世界空间画线(半透明方块阶段之后;1.21.8 起按 stage 子事件分发)。
        // 21.10 的 stage 事件撤掉了 getCamera(),相机改走 gameRenderer 主相机。
        com.dwinovo.numen.client.debug.PathDebugRenderer.render(
                event.getPoseStack(),
                net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    static void registerKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        // N → companion roster panel (chat entry + settings/reset live in there).
        event.register(com.dwinovo.numen.client.NumenKeys.OPEN_ROSTER);
    }

    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        com.dwinovo.numen.client.NumenKeys.tick();
        com.dwinovo.numen.client.hud.NumenToasts.tick();
        com.dwinovo.numen.client.agent.AgentLoopRegistry.tickAll();
        com.dwinovo.numen.client.agent.CompanionCameraSync.tick();
    }

    static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        // 先掐大脑:作废在飞回合与工具链,别让上一个存档的回合漂进下一个存档
        com.dwinovo.numen.client.agent.AgentLoopRegistry.quiesceAll();
        com.dwinovo.numen.client.data.ClientNumenInventory.clear();
        com.dwinovo.numen.client.agent.KnownSkins.clear();
        com.dwinovo.numen.client.hud.NumenToasts.clear();
        com.dwinovo.numen.client.agent.ClientDeaths.clearAll();
        com.dwinovo.numen.client.debug.PathDebugState.clear();
    }

    static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        // HUD: advancement-style activity toasts (top-right) when not watching a panel.
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "numen_toasts"),
                (g, delta) -> com.dwinovo.numen.client.hud.NumenToasts.render(g));
    }

    // 1.21.5 起 shader 走代码定义的 RenderPipeline,首次使用时懒编译,无需任何
    // loader 侧注册(RegisterShadersEvent 已随 JSON shader 体系一并移除)。

    static void registerReloadListeners(AddClientReloadListenersEvent event) {
        // 1.21.4 uses AddClientReloadListenersEvent.addListener(Identifier, listener) —
        // the keyed API (1.21.1 was RegisterClientReloadListenersEvent.registerReloadListener,
        // no key).
        Path numenConfigRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve(Constants.MOD_ID);
        Path skillsDir = numenConfigRoot.resolve("skills");

        event.addListener(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "skill_loader"),
                (ResourceManagerReloadListener) rm -> {
                    SkillRegistry.instance().scan(skillsDir);
                });
    }
}
