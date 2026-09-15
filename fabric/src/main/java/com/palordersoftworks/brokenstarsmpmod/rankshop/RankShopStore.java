package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence for Rank Token balances and temporary-rank expirations.
 *
 * Stored as JSON under {@code config/brokenstarsmp/data/rankshop.json} and written
 * synchronously on every mutation (same crash-safety model as BanknoteStore):
 * a purchase/conversion is authoritative in memory only after its save succeeded,
 * so a crash can never restore spent tokens.
 */
public final class RankShopStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BALANCE_TYPE = new TypeToken<Map<String, Long>>() {}.getType();
    private static final Type EXPIRY_TYPE = new TypeToken<Map<String, Long>>() {}.getType();

    private final Path file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    /** rank id -> epoch millis when the temporary grant expires. */
    private final Map<UUID, Map<String, Long>> expirations = new ConcurrentHashMap<>();

    public RankShopStore(MinecraftServer server) {
        Path dir = server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Could not create rank shop data dir", e);
        }
        this.file = dir.resolve("rankshop.json");
        load();
    }

    // ---- balances -----------------------------------------------------------

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    /** @return true if the balance was changed (guards against negative results). */
    public synchronized boolean setBalance(UUID player, long amount) {
        if (amount < 0) {
            return false;
        }
        Long previous = balances.get(player);
        if (amount == 0) {
            balances.remove(player);
        } else {
            balances.put(player, amount);
        }
        if (!save()) {
            LOGGER.error("[BrokenStars] CRITICAL: token balance set for {} was not persisted; rolling back in memory", player);
            if (previous == null) {
                balances.remove(player);
            } else {
                balances.put(player, previous);
            }
            return false;
        }
        return true;
    }

    /** @return true if the player held at least {@code amount} tokens, which are then removed. */
    public synchronized boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return false;
        }
        long balance = getBalance(player);
        if (balance < amount) {
            return false;
        }
        long updated = balance - amount;
        if (updated == 0) {
            balances.remove(player);
        } else {
            balances.put(player, updated);
        }
        if (!save()) {
            LOGGER.error("[BrokenStars] CRITICAL: token withdrawal for {} was not persisted; rolling back in memory", player);
            balances.put(player, balance);
            return false;
        }
        return true;
    }

    public synchronized void deposit(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        long updated = getBalance(player) + amount;
        balances.put(player, updated);
        if (!save()) {
            LOGGER.error("[BrokenStars] CRITICAL: token deposit for {} was not persisted", player);
        }
    }

    // ---- rank expirations ----------------------------------------------------

    public Long getExpiration(UUID player, String rankId) {
        Map<String, Long> byRank = expirations.get(player);
        return byRank == null ? null : byRank.get(rankId);
    }

    public synchronized void recordExpiration(UUID player, String rankId, long epochMillis) {
        expirations.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(rankId, epochMillis);
        save();
    }

    public synchronized void clearExpiration(UUID player, String rankId) {
        Map<String, Long> byRank = expirations.get(player);
        if (byRank != null) {
            byRank.remove(rankId);
            if (byRank.isEmpty()) {
                expirations.remove(player);
            }
            save();
        }
    }

    /** Player -> (rank id -> epoch millis). Defensive copy for safe iteration. */
    public synchronized Map<UUID, Map<String, Long>> expirationsSnapshot() {
        Map<UUID, Map<String, Long>> copy = new ConcurrentHashMap<>();
        expirations.forEach((player, byRank) -> copy.put(player, new java.util.HashMap<>(byRank)));
        return copy;
    }

    // ---- persistence ---------------------------------------------------------

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> root = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            if (root == null) {
                return;
            }
            if (root.get("balances") instanceof Map<?, ?> rawBalances) {
                for (Map.Entry<?, ?> entry : rawBalances.entrySet()) {
                    try {
                        balances.put(UUID.fromString(entry.getKey().toString()),
                                ((Number) entry.getValue()).longValue());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (root.get("expirations") instanceof Map<?, ?> rawExpirations) {
                for (Map.Entry<?, ?> entry : rawExpirations.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> byRank)) {
                        continue;
                    }
                    Map<String, Long> parsed = new ConcurrentHashMap<>();
                    for (Map.Entry<?, ?> rankEntry : byRank.entrySet()) {
                        if (rankEntry.getValue() instanceof Number number) {
                            parsed.put(rankEntry.getKey().toString(), number.longValue());
                        }
                    }
                    try {
                        expirations.put(UUID.fromString(entry.getKey().toString()), parsed);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] Failed to load rank shop data", e);
        }
    }

    private boolean save() {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        Map<String, Long> balanceOut = new java.util.TreeMap<>();
        balances.forEach((uuid, amount) -> balanceOut.put(uuid.toString(), amount));
        root.put("balances", balanceOut);
        Map<String, Map<String, Long>> expiryOut = new java.util.TreeMap<>();
        expirations.forEach((uuid, byRank) -> {
            Map<String, Long> copy = new java.util.TreeMap<>(byRank);
            expiryOut.put(uuid.toString(), copy);
        });
        root.put("expirations", expiryOut);
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.error("[BrokenStars] Failed to save rank shop data", e);
            return false;
        }
    }
}
