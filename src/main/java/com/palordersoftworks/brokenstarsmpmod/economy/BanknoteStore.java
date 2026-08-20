package com.palordersoftworks.brokenstarsmpmod.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class BanknoteStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Set<String> redeemed = new HashSet<>();

    public BanknoteStore(MinecraftServer server) {
        Path dir = server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Could not create banknote data dir", e);
        }
        this.file = dir.resolve("banknote_signatures.json");
        load();
    }

    /** @return true if this signature is newly redeemed; false if already used (dupe). */
    public synchronized boolean markRedeemed(String signature) {
        if (signature == null || signature.isBlank()) return false;
        if (!redeemed.add(signature)) {
            return false;
        }
        save();
        return true;
    }

    public synchronized void save() {
        try {
            Files.writeString(file, GSON.toJson(redeemed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Failed to save banknote signatures", e);
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Set<String> loaded = GSON.fromJson(json, new TypeToken<Set<String>>() {}.getType());
            redeemed.clear();
            if (loaded != null) {
                redeemed.addAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] Failed to load banknote signatures", e);
        }
    }
}
