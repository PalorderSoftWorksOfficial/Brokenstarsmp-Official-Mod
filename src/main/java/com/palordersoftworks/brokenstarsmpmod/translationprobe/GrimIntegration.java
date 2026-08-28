package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GrimIntegration implements ModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentHashMap<UUID, Long> LAST_PROBE = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;
    private static volatile GrimPlugin plugin;
    private static volatile boolean registered;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(GrimIntegration::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(GrimIntegration::onServerStopping);
    }

    private static void onServerStarted(MinecraftServer minecraftServer) {
        server = minecraftServer;
        try {
            GrimAbstractAPI api = GrimAPIProvider.get();
            plugin = api.getGrimPlugin("brokenstarsmp");
            api.getEventBus().get(FlagEvent.class).onFlagSupplier(plugin, GrimIntegration::onFlag, 100, false);
            registered = true;
            LOGGER.info("[BrokenStarSMP/Grim] GrimAC integration enabled for version {}", api.getGrimVersion());
        } catch (Throwable throwable) {
            registered = false;
            LOGGER.warn("[BrokenStarSMP/Grim] GrimAC integration could not be enabled", throwable);
        }
    }

    private static boolean onFlag(GrimUser user, AbstractCheck check, java.util.function.Supplier<String> verbose, boolean cancelled) {
        if (!registered || cancelled || user == null) {
            return cancelled;
        }

        CheckHacksConfig cfg = TranslationProbeController.getFileConfig();
        if (cfg == null || !cfg.enabled || cfg.detectFlag == null || !cfg.detectFlag.enabled) {
            return cancelled;
        }

        boolean grimEnabled = cfg.detectFlag.anticheats == null || cfg.detectFlag.anticheats.isEmpty()
                || cfg.detectFlag.anticheats.getOrDefault("grimac", false);
        if (!grimEnabled) {
            return cancelled;
        }

        long cooldownMillis = Math.max(0L, cfg.detectFlag.cooldownHours) * 60L * 60L * 1000L;
        UUID uuid = user.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = LAST_PROBE.putIfAbsent(uuid, now);
        if (previous != null && now - previous < cooldownMillis) {
            return cancelled;
        }
        LAST_PROBE.put(uuid, now);

        MinecraftServer currentServer = server;
        if (currentServer != null) {
            currentServer.execute(() -> {
                ServerPlayer player = currentServer.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    TranslationProbeController.startPlayerCheck(player, currentServer, cfg.detectFlag.hacks);
                    LOGGER.info("[BrokenStarSMP/Grim] Translation probe triggered for {} after Grim flag {}", user.getName(), check.getCheckName());
                } else {
                    LAST_PROBE.remove(uuid, now);
                }
            });
        } else {
            LAST_PROBE.remove(uuid, now);
        }

        return cancelled;
    }

    private static void onServerStopping(MinecraftServer minecraftServer) {
        if (server == minecraftServer) {
            server = null;
        }
        LAST_PROBE.clear();
        registered = false;
        plugin = null;
    }
}
