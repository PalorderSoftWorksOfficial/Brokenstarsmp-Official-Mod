package com.palordersoftworks.economycraft.playervault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player numbered vaults (virtual chests).
 */
public final class PlayerVaultManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;
    /** uuid -> vault index -> backing inventory */
    private final Map<UUID, Map<Integer, SimpleInventory>> cache = new HashMap<>();

    public PlayerVaultManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getRunDirectory().resolve("config").resolve(EconomyConfig.CONFIG_FOLDER_NAME).resolve("data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Could not create data directory for player vaults", e);
        }
        this.file = dir.resolve("playervaults.json");
        load();
    }

    public void removePlayer(UUID id) {
        cache.remove(id);
        save();
    }

    /**
     * Returns a 9 * rows slot inventory for the given vault index, loading or creating as needed.
     */
    public SimpleInventory getOrCreateVault(UUID owner, int vaultIndex, int rows) {
        int size = rows * 9;
        Map<Integer, SimpleInventory> forPlayer = cache.computeIfAbsent(owner, u -> new HashMap<>());
        return forPlayer.computeIfAbsent(vaultIndex, i -> new SimpleInventory(size));
    }

    /**
     * Ensures the backing inventory has the correct size (migration if rows changed).
     */
    public SimpleInventory prepareVault(UUID owner, int vaultIndex, int rows) {
        int size = rows * 9;
        Map<Integer, SimpleInventory> forPlayer = cache.computeIfAbsent(owner, u -> new HashMap<>());
        SimpleInventory existing = forPlayer.get(vaultIndex);
        if (existing == null) {
            SimpleInventory inv = new SimpleInventory(size);
            forPlayer.put(vaultIndex, inv);
            return inv;
        }
        if (existing.size() == size) {
            return existing;
        }
        SimpleInventory resized = new SimpleInventory(size);
        int copyCount = Math.min(existing.size(), size);
        for (int i = 0; i < copyCount; i++) {
            resized.setStack(i, existing.getStack(i).copy());
        }
        forPlayer.put(vaultIndex, resized);
        save();
        return resized;
    }

    public void save() {
        var ops = server.getRegistryManager().getOps(JsonOps.INSTANCE);
        JsonObject root = new JsonObject();
        for (var ePlayer : cache.entrySet()) {
            JsonObject vaults = new JsonObject();
            for (var eVault : ePlayer.getValue().entrySet()) {
                JsonArray slots = new JsonArray();
                SimpleInventory inv = eVault.getValue();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (stack.isEmpty()) {
                        slots.add(JsonNull.INSTANCE);
                    } else {
                        var enc = ItemStack.CODEC.encodeStart(ops, stack).result();
                        slots.add(enc.orElseGet(JsonObject::new));
                    }
                }
                vaults.add(String.valueOf(eVault.getKey()), slots);
            }
            root.add(ePlayer.getKey().toString(), vaults);
        }
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[EconomyCraft] Failed to save playervaults.json", e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            var ops = server.getRegistryManager().getOps(JsonOps.INSTANCE);
            cache.clear();
            for (var pe : root.entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(pe.getKey());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!pe.getValue().isJsonObject()) continue;
                JsonObject vaultObj = pe.getValue().getAsJsonObject();
                Map<Integer, SimpleInventory> map = new HashMap<>();
                for (var ve : vaultObj.entrySet()) {
                    int idx;
                    try {
                        idx = Integer.parseInt(ve.getKey());
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (!ve.getValue().isJsonArray()) continue;
                    JsonArray arr = ve.getValue().getAsJsonArray();
                    int size = arr.size();
                    SimpleInventory inv = new SimpleInventory(size);
                    for (int i = 0; i < size; i++) {
                        JsonElement el = arr.get(i);
                        if (el == null || el.isJsonNull()) {
                            continue;
                        }
                        ItemStack stack = ItemStack.CODEC.parse(ops, el).result().orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty()) {
                            inv.setStack(i, stack);
                        }
                    }
                    map.put(idx, inv);
                }
                cache.put(uuid, map);
            }
            LOGGER.info("[EconomyCraft] Loaded player vault data for {} players.", cache.size());
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to load playervaults.json", e);
        }
    }
}
