package com.palordersoftworks.brokenstarsmpmod.economy.playervault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class PlayerVaultManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;
    private final Map<UUID, Map<Integer, SimpleContainer>> cache = new HashMap<>();
    private final Map<UUID, Integer> unlockedCounts = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> vaultNames = new HashMap<>();

    public PlayerVaultManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Could not create player vault data dir", e);
        }
        this.file = dir.resolve("playervaults.json");
        load();
    }

    public void removePlayer(UUID id) {
        cache.remove(id);
        unlockedCounts.remove(id);
        vaultNames.remove(id);
        save();
    }

    public Set<UUID> getTrackedPlayers() {
        Set<UUID> players = new LinkedHashSet<>();
        players.addAll(cache.keySet());
        players.addAll(unlockedCounts.keySet());
        players.addAll(vaultNames.keySet());
        return Collections.unmodifiableSet(players);
    }

    public void clearVault(UUID owner, int vaultIndex) {
        if (owner == null || vaultIndex < 1) return;
        Map<Integer, SimpleContainer> forPlayer = cache.get(owner);
        if (forPlayer == null) return;
        SimpleContainer vault = forPlayer.get(vaultIndex);
        if (vault == null) return;
        for (int i = 0; i < vault.getContainerSize(); i++) {
            vault.setItem(i, ItemStack.EMPTY);
        }
        save();
    }

    public void deleteVault(UUID owner, int vaultIndex) {
        if (owner == null || vaultIndex < 1) return;
        Map<Integer, SimpleContainer> forPlayer = cache.get(owner);
        if (forPlayer != null) {
            forPlayer.remove(vaultIndex);
            if (forPlayer.isEmpty()) cache.remove(owner);
        }
        Map<Integer, String> names = vaultNames.get(owner);
        if (names != null) {
            names.remove(vaultIndex);
            if (names.isEmpty()) vaultNames.remove(owner);
        }
        int unlocked = unlockedCounts.getOrDefault(owner, 1);
        if (vaultIndex >= unlocked) {
            unlockedCounts.put(owner, Math.max(1, unlocked - 1));
        }
        save();
    }

    public void clearAllVaults(UUID owner) {
        if (owner == null) return;
        Map<Integer, SimpleContainer> forPlayer = cache.get(owner);
        if (forPlayer != null) {
            for (SimpleContainer vault : forPlayer.values()) {
                for (int i = 0; i < vault.getContainerSize(); i++) {
                    vault.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        save();
    }

    public String getVaultName(UUID owner, int vaultIndex) {
        if (owner == null || vaultIndex < 1) return null;
        Map<Integer, String> map = vaultNames.get(owner);
        if (map == null) return null;
        String name = map.get(vaultIndex);
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void setVaultName(UUID owner, int vaultIndex, String newName) {
        if (owner == null || vaultIndex < 1) return;
        Map<Integer, String> names = vaultNames.computeIfAbsent(owner, u -> new HashMap<>());
        if (newName == null || newName.isBlank()) {
            names.remove(vaultIndex);
        } else {
            String trimmed = newName.trim();
            if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);
            names.put(vaultIndex, trimmed);
        }
        if (names.isEmpty()) vaultNames.remove(owner);
        save();
    }

    public int getUnlockedVaultCount(UUID owner, int allowedMax) {
        int max = Math.max(0, allowedMax);
        if (max <= 0) return 0;
        int current = unlockedCounts.getOrDefault(owner, 1);
        int clamped = Math.min(max, Math.max(1, current));
        if (current != clamped) unlockedCounts.put(owner, clamped);
        return clamped;
    }

    public int tryUnlockNextVault(UUID owner, int allowedMax) {
        int unlocked = getUnlockedVaultCount(owner, allowedMax);
        if (unlocked >= Math.max(0, allowedMax)) return unlocked;
        int next = unlocked + 1;
        unlockedCounts.put(owner, next);
        save();
        return next;
    }

    public SimpleContainer prepareVault(UUID owner, int vaultIndex, int rows) {
        int size = rows * 9;
        Map<Integer, SimpleContainer> forPlayer = cache.computeIfAbsent(owner, u -> new HashMap<>());
        SimpleContainer existing = forPlayer.get(vaultIndex);
        if (existing == null) {
            SimpleContainer inv = new SimpleContainer(size);
            forPlayer.put(vaultIndex, inv);
            return inv;
        }
        if (existing.getContainerSize() == size) return existing;
        SimpleContainer resized = new SimpleContainer(size);
        int copyCount = Math.min(existing.getContainerSize(), size);
        for (int i = 0; i < copyCount; i++) {
            resized.setItem(i, existing.getItem(i).copy());
        }
        forPlayer.put(vaultIndex, resized);
        save();
        return resized;
    }

    public void save() {
        try {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject root = new JsonObject();
            for (UUID playerId : getTrackedPlayers()) {
                JsonObject playerObj = new JsonObject();
                JsonObject meta = new JsonObject();
                meta.addProperty("unlocked", unlockedCounts.getOrDefault(playerId, 1));
                Map<Integer, String> names = vaultNames.get(playerId);
                if (names != null && !names.isEmpty()) {
                    JsonObject namesObj = new JsonObject();
                    for (Map.Entry<Integer, String> ne : names.entrySet()) {
                        if (ne.getValue() != null) {
                            namesObj.addProperty(String.valueOf(ne.getKey()), ne.getValue());
                        }
                    }
                    meta.add("names", namesObj);
                }
                playerObj.add("_meta", meta);

                Map<Integer, SimpleContainer> forPlayer = cache.get(playerId);
                if (forPlayer != null) {
                    for (Map.Entry<Integer, SimpleContainer> ve : forPlayer.entrySet()) {
                        JsonArray arr = new JsonArray();
                        SimpleContainer inv = ve.getValue();
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            ItemStack stack = inv.getItem(i);
                            try {
                                arr.add(ItemStack.CODEC.encodeStart(ops, stack).result().orElse(JsonNull.INSTANCE));
                            } catch (Exception e) {
                                arr.add(JsonNull.INSTANCE);
                            }
                        }
                        playerObj.add(String.valueOf(ve.getKey()), arr);
                    }
                }
                root.add(String.valueOf(playerId), playerObj);
            }
            Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Failed to save player vaults", e);
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            cache.clear();
            unlockedCounts.clear();
            vaultNames.clear();
            for (var pe : parsed.getAsJsonObject().entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(pe.getKey());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!pe.getValue().isJsonObject()) continue;
                JsonObject vaultObj = pe.getValue().getAsJsonObject();
                Map<Integer, SimpleContainer> map = new HashMap<>();
                Map<Integer, String> names = new HashMap<>();
                int unlocked = 1;
                for (var ve : vaultObj.entrySet()) {
                    if ("_meta".equals(ve.getKey()) && ve.getValue().isJsonObject()) {
                        JsonObject meta = ve.getValue().getAsJsonObject();
                        if (meta.has("unlocked")) {
                            try {
                                unlocked = Math.max(1, meta.get("unlocked").getAsInt());
                            } catch (Exception ignored) {
                                unlocked = 1;
                            }
                        }
                        if (meta.has("names") && meta.get("names").isJsonObject()) {
                            for (var ne : meta.getAsJsonObject("names").entrySet()) {
                                try {
                                    int idx = Integer.parseInt(ne.getKey());
                                    if (idx >= 1 && ne.getValue().isJsonPrimitive()) {
                                        String val = ne.getValue().getAsString().trim();
                                        if (!val.isEmpty()) {
                                            if (val.length() > 32) val = val.substring(0, 32);
                                            names.put(idx, val);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
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
                    SimpleContainer inv = new SimpleContainer(arr.size());
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (el == null || el.isJsonNull()) continue;
                        ItemStack stack = ItemStack.CODEC.parse(ops, el).result().orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty()) inv.setItem(i, stack);
                    }
                    map.put(idx, inv);
                }
                cache.put(uuid, map);
                unlockedCounts.put(uuid, unlocked);
                if (!names.isEmpty()) vaultNames.put(uuid, names);
            }
            LOGGER.info("[BrokenStars] Loaded player vaults for {} players.", cache.size());
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] Failed to load playervaults.json", e);
        }
    }
}
