package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code config/brokenstarsmp/translation_probe_config.json} (check-hacks registry).
 * If the file is missing, writes {@link CheckHacksConfig#createDefaultRegistry()}.
 * If {@code hacks} is empty after parse, merges the built-in default registry.
 */
public final class TranslationProbeStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TranslationProbeStorage() {}

    public static Path configPath(MinecraftServer server) {
        return server.getRunDirectory().resolve("config").resolve("brokenstarsmp").resolve("translation_probe_config.json");
    }

    public static CheckHacksConfig load(MinecraftServer server) {
        Path path = configPath(server);
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                CheckHacksConfig fresh = CheckHacksConfig.createDefaultRegistry();
                Files.writeString(path, GSON.toJson(fresh), StandardCharsets.UTF_8);
                LOGGER.info("[BrokenStarSMP/CheckHacks] Created default registry at {}", path.toAbsolutePath());
                return fresh;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            CheckHacksConfig parsed = GSON.fromJson(json, CheckHacksConfig.class);
            if (parsed == null) {
                parsed = CheckHacksConfig.createDefaultRegistry();
            }
            if (parsed.defaultCheckHacks == null) {
                parsed.defaultCheckHacks = new java.util.ArrayList<>();
            }
            if (parsed.hacks == null || parsed.hacks.isEmpty()) {
                parsed.mergeMissingHacksFrom(CheckHacksConfig.createDefaultRegistry());
            }
            parsed.normalizeHackIds();
            return parsed;
        } catch (Exception e) {
            LOGGER.error("[BrokenStarSMP/CheckHacks] Failed to load config; using in-memory defaults.", e);
            return CheckHacksConfig.createDefaultRegistry();
        }
    }
}
