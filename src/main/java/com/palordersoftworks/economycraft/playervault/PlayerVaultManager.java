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
    /** uuid -> currently unlocked vault count */
    private final Map<UUID, Integer> unlockedCounts = new HashMap<>();

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
        unlockedCounts.remove(id);
        save();
    }

    public int getUnlockedVaultCount(UUID owner, int allowedMax) {
        int max = Math.max(0, allowedMax);
        if (max <= 0) {
            return 0;
        }
        int current = unlockedCounts.getOrDefault(owner, 1);
        int clamped = Math.min(max, Math.max(1, current));
        if (current != clamped) {
            unlockedCounts.put(owner, clamped);
        }
        return clamped;
    }

    public int tryUnlockNextVault(UUID owner, int allowedMax) {
        int unlocked = getUnlockedVaultCount(owner, allowedMax);
        if (unlocked >= Math.max(0, allowedMax)) {
            return unlocked;
        }
        int next = unlocked + 1;
        unlockedCounts.put(owner, next);
        save();
        return next;
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
        java.util.Set<UUID> players = new java.util.LinkedHashSet<>();
        players.addAll(cache.keySet());
        players.addAll(unlockedCounts.keySet());
        for (UUID playerId : players) {
            Map<Integer, SimpleInventory> playerVaults = cache.getOrDefault(playerId, Map.of());
            JsonObject vaults = new JsonObject();
            JsonObject meta = new JsonObject();
            int unlocked = getUnlockedVaultCount(playerId, Integer.MAX_VALUE);
            meta.addProperty("unlocked", unlocked);
            vaults.add("_meta", meta);
            for (var eVault : playerVaults.entrySet()) {
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
            root.add(playerId.toString(), vaults);
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
            unlockedCounts.clear();
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
                int unlocked = 1;
                for (var ve : vaultObj.entrySet()) {
                    if ("_meta".equals(ve.getKey()) && ve.getValue().isJsonObject()) {
                        JsonObject meta = ve.getValue().getAsJsonObject();
                        if (meta.has("unlocked") && meta.get("unlocked").isJsonPrimitive()
                                && meta.get("unlocked").getAsJsonPrimitive().isNumber()) {
                            try {
                                unlocked = Math.max(1, meta.get("unlocked").getAsInt());
                            } catch (Exception ignored) {
                                unlocked = 1;
                            }
                        }
                        continue;
                    }
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
                unlockedCounts.put(uuid, unlocked);
            }
            LOGGER.info("[EconomyCraft] Loaded player vault data for {} players.", cache.size());
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to load playervaults.json", e);
        }
    }
}
