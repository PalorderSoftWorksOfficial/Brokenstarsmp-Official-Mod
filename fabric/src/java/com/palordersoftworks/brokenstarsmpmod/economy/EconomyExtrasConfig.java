package com.palordersoftworks.brokenstarsmpmod.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class EconomyExtrasConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EconomyExtrasConfig INSTANCE = new EconomyExtrasConfig();

    public boolean playerVaultEnabled = true;
    public boolean playerVaultRequirePermission = false;
    public int playerVaultRows = 6;
    public int playerVaultDefaultAmount = 1;
    public int playerVaultMaxAmount = 54;
    public String playerVaultLuckPermsMetaKey = "brokenstarsmp.economy.playervault.amount";
    public long playerVaultUnlockCost = 0L;
    public boolean banknotesEnabled = true;
    public boolean sellWandEnabled = true;

    private EconomyExtrasConfig() {}

    public static EconomyExtrasConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path file = configFile(server);
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                INSTANCE = new EconomyExtrasConfig();
                INSTANCE.clamp();
                save(server);
                return;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EconomyExtrasConfig loaded = GSON.fromJson(json, EconomyExtrasConfig.class);
            if (loaded == null) {
                loaded = new EconomyExtrasConfig();
            }
            loaded.clamp();
            INSTANCE = loaded;
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] Failed to load economy-extras.json", e);
            INSTANCE = new EconomyExtrasConfig();
            INSTANCE.clamp();
        }
    }

    public static void save(MinecraftServer server) {
        Path file = configFile(server);
        try {
            Files.createDirectories(file.getParent());
            INSTANCE.clamp();
            Files.writeString(file, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Failed to save economy-extras.json", e);
        }
    }

    private static Path configFile(MinecraftServer server) {
        return server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("economy-extras.json");
    }

    private void clamp() {
        if (playerVaultRows < 1) playerVaultRows = 1;
        if (playerVaultRows > 6) playerVaultRows = 6;
        if (playerVaultDefaultAmount < 0) playerVaultDefaultAmount = 0;
        if (playerVaultMaxAmount < 1) playerVaultMaxAmount = 54;
        if (playerVaultLuckPermsMetaKey == null || playerVaultLuckPermsMetaKey.isBlank()) {
            playerVaultLuckPermsMetaKey = "brokenstarsmp.economy.playervault.amount";
        }
        if (playerVaultUnlockCost < 0) playerVaultUnlockCost = 0;
    }
}
