package com.palordersoftworks.economycraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;

public class EconomyManager {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();
    private static final Type DAILY_SELL_TYPE = new TypeToken<Map<UUID, DailySellData>>(){}.getType();

    private final MinecraftServer server;
    private final Path file;
    private final Path shardsFile;
    private final Path dailyFile;
    private final Path dailySellFile;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> shards = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private final Map<UUID, DailySellData> dailySells = new HashMap<>();
    private Map<UUID, String> diskUserCache = null;
    private final PriceRegistry prices;

    private ScoreboardObjective objective;
    private final com.palordersoftworks.economycraft.shop.ShopManager shop;
    private final com.palordersoftworks.economycraft.orders.OrderManager orders;
    private final com.palordersoftworks.economycraft.playervault.PlayerVaultManager playerVaults;
    private final Set<UUID> displayed = new HashSet<>();

    /** Target player (who must /eco coinflip accept) -> pending offer. */
    private final Map<UUID, CoinflipOffer> coinflipPending = new ConcurrentHashMap<>();

    public static final long MAX = 999_999_999L;

    public record CoinflipOffer(UUID challengerId, long amount, long expiryEpochMs) {}

    public record CoinflipAcceptResult(boolean ok, String errorMessage, UUID winnerId, UUID loserId, long payout) {
        public static CoinflipAcceptResult fail(String m) {
            return new CoinflipAcceptResult(false, m, null, null, 0L);
        }

        public static CoinflipAcceptResult ok(UUID winner, UUID loser, long payout) {
            return new CoinflipAcceptResult(true, null, winner, loser, payout);
        }
    }

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        Path dir = server.getRunDirectory().resolve("config").resolve(EconomyConfig.CONFIG_FOLDER_NAME);
        Path dataDir = dir.resolve("data");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}

        this.file = dataDir.resolve("balances.json");
        this.shardsFile = dataDir.resolve("shards.json");
        this.dailyFile = dataDir.resolve("daily.json");
        this.dailySellFile = dataDir.resolve("daily_sells.json");

        load();
        loadShards();
        loadDaily();
        loadDailySells();

        this.shop = new com.palordersoftworks.economycraft.shop.ShopManager(server);
        this.orders = new com.palordersoftworks.economycraft.orders.OrderManager(server);
        this.playerVaults = new com.palordersoftworks.economycraft.playervault.PlayerVaultManager(server);

        applyScoreboardSettingOnStartup();
        this.prices = new PriceRegistry(server);
    }

    public MinecraftServer getServer() {
        return server;
    }

    // =====================================================================
    // === Name handling ================================
    // =====================================================================

    private void ensureDiskUserCacheLoaded() {
        if (diskUserCache != null) return;
        diskUserCache = new HashMap<>();
        try {
            Path uc = server.getRunDirectory().resolve("usercache.json");
            if (!Files.exists(uc)) return;

            String json = Files.readString(uc);
            UserCacheEntry[] entries = GSON.fromJson(json, UserCacheEntry[].class);
            if (entries == null) return;

            for (UserCacheEntry e : entries) {
                if (e == null || e.uuid == null || e.uuid.isBlank() || e.name == null || e.name.isBlank()) continue;
                try {
                    diskUserCache.put(UUID.fromString(e.uuid), e.name);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private String resolveName(MinecraftServer server, UUID id) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) return IdentityCompat.of(online.getGameProfile()).name();
        ensureDiskUserCacheLoaded();
        String fromDisk = diskUserCache.get(id);
        if (fromDisk != null && !fromDisk.isBlank()) return fromDisk;
        return id.toString();
    }

    /** Resolved display name for commands/UI (online, usercache, or UUID string). */
    public String getBestName(UUID id) {
        return resolveName(server, id);
    }

    public UUID tryResolveUuidByName(String name) {
        if (name == null || name.isBlank()) return null;
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) return online.getUuid();

        ensureDiskUserCacheLoaded();
        for (var e : diskUserCache.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue())) return e.getKey();
        }

        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) {}

        return null;
    }

    // =====================================================================
    // === Balances ========================================================
    // =====================================================================

    public Long getBalance(UUID player, boolean newBalanceIfNonExistent) {
        if (!balances.containsKey(player)) {
            if (newBalanceIfNonExistent) {
                long balance = clamp(EconomyConfig.get().startingBalance);
                balances.put(player, balance);
                updateLeaderboard();
                return balance;
            } else {
                return null;
            }
        }
        return balances.get(player);
    }

    public void addMoney(UUID player, long amount) {
        balances.put(player, clamp(getBalance(player, true) + amount));
        updateLeaderboard();
        save();
    }

    public void setMoney(UUID player, long amount) {
        balances.put(player, clamp(amount));
        updateLeaderboard();
        save();
    }

    public boolean removeMoney(UUID player, long amount) {
        long balance = getBalance(player, true);
        if (balance < amount) return false;
        balances.put(player, clamp(balance - amount));
        updateLeaderboard();
        save();
        return true;
    }

    public boolean pay(UUID from, UUID to, long amount) {
        long balance = getBalance(from, false);
        if (balance < amount) return false;
        removeMoney(from, amount);
        addMoney(to, amount);
        return true;
    }

    // =====================================================================
    // === Shards (secondary currency) ======================================
    // =====================================================================

    public long getShards(UUID player, boolean createIfMissing) {
        if (!EconomyConfig.get().shardsEnabled) {
            return 0L;
        }
        if (!shards.containsKey(player)) {
            if (createIfMissing) {
                long v = clampShard(EconomyConfig.get().startingShards);
                shards.put(player, v);
                saveShards();
                return v;
            }
            return 0L;
        }
        return shards.get(player);
    }

    public void addShards(UUID player, long amount) {
        if (!EconomyConfig.get().shardsEnabled || amount == 0) return;
        long cur = getShards(player, true);
        shards.put(player, clampShard(cur + amount));
        saveShards();
    }

    public boolean removeShards(UUID player, long amount) {
        if (!EconomyConfig.get().shardsEnabled) return false;
        long cur = getShards(player, true);
        if (cur < amount) return false;
        shards.put(player, clampShard(cur - amount));
        saveShards();
        return true;
    }

    public boolean shardPay(UUID from, UUID to, long amount) {
        if (!removeShards(from, amount)) return false;
        addShards(to, amount);
        return true;
    }

    public Map<UUID, Long> getShardBalances() {
        return Collections.unmodifiableMap(shards);
    }

    public void setShards(UUID player, long amount) {
        if (!EconomyConfig.get().shardsEnabled) return;
        shards.put(player, clampShard(amount));
        saveShards();
    }

    private long clampShard(long value) {
        return Math.max(0L, Math.min(MAX, value));
    }

    private void loadShards() {
        if (!Files.exists(shardsFile)) return;
        try {
            String json = Files.readString(shardsFile);
            Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
            if (map != null) {
                for (Map.Entry<UUID, Double> e : map.entrySet()) {
                    shards.put(e.getKey(), clampShard(e.getValue().longValue()));
                }
            }
        } catch (IOException ignored) {}
    }

    private void saveShards() {
        if (!EconomyConfig.get().shardsEnabled) return;
        try {
            String json = GSON.toJson(new HashMap<>(shards), TYPE);
            Files.writeString(shardsFile, json);
        } catch (IOException ignored) {}
    }

    // =====================================================================
    // === Coinflip (money wager) ===========================================
    // =====================================================================

    public void purgeExpiredCoinflips() {
        long now = System.currentTimeMillis();
        for (var it = coinflipPending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().expiryEpochMs() < now) {
                addMoney(e.getValue().challengerId(), e.getValue().amount());
                it.remove();
            }
        }
    }

    /**
     * @return null on success, or error message
     */
    @Nullable
    public String tryOfferCoinflip(ServerPlayerEntity challenger, ServerPlayerEntity target, long amount) {
        if (!EconomyConfig.get().coinflipEnabled) {
            return "Coinflip is disabled.";
        }
        if (amount < 1 || amount > MAX) {
            return "Invalid amount.";
        }
        if (challenger.getUuid().equals(target.getUuid())) {
            return "You cannot coinflip yourself.";
        }
        purgeExpiredCoinflips();
        if (coinflipPending.containsKey(target.getUuid())) {
            return "That player already has a pending coinflip.";
        }
        if (!removeMoney(challenger.getUuid(), amount)) {
            return "You cannot afford that.";
        }
        long timeoutMs = EconomyConfig.get().coinflipTimeoutSeconds * 1000L;
        coinflipPending.put(target.getUuid(), new CoinflipOffer(challenger.getUuid(), amount, System.currentTimeMillis() + timeoutMs));
        return null;
    }

    public CoinflipAcceptResult tryAcceptCoinflip(ServerPlayerEntity target) {
        if (!EconomyConfig.get().coinflipEnabled) {
            return CoinflipAcceptResult.fail("Coinflip is disabled.");
        }
        purgeExpiredCoinflips();
        CoinflipOffer offer = coinflipPending.remove(target.getUuid());
        if (offer == null) {
            return CoinflipAcceptResult.fail("You have no pending coinflip.");
        }
        if (System.currentTimeMillis() > offer.expiryEpochMs()) {
            addMoney(offer.challengerId(), offer.amount());
            return CoinflipAcceptResult.fail("That coinflip expired; the challenger was refunded.");
        }
        if (!removeMoney(target.getUuid(), offer.amount())) {
            addMoney(offer.challengerId(), offer.amount());
            return CoinflipAcceptResult.fail("You cannot afford that stake; the challenger was refunded.");
        }
        long pot = 2L * offer.amount();
        double tax = EconomyConfig.get().coinflipTaxRate;
        long taxAmt = (long) Math.floor(pot * tax);
        long payout = clamp(pot - taxAmt);
        boolean challengerWins = server.getOverworld().getRandom().nextBoolean();
        UUID winner = challengerWins ? offer.challengerId() : target.getUuid();
        UUID loser = challengerWins ? target.getUuid() : offer.challengerId();
        addMoney(winner, payout);
        return CoinflipAcceptResult.ok(winner, loser, payout);
    }

    /**
     * Challenger cancels their outgoing offer (refund if still waiting on this target).
     */
    @Nullable
    public String tryCancelCoinflip(ServerPlayerEntity challenger) {
        purgeExpiredCoinflips();
        for (var it = coinflipPending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().challengerId().equals(challenger.getUuid())) {
                addMoney(challenger.getUuid(), e.getValue().amount());
                it.remove();
                return null;
            }
        }
        return "You have no pending coinflip to cancel.";
    }

    @Nullable
    public CoinflipOffer peekCoinflipFor(ServerPlayerEntity target) {
        purgeExpiredCoinflips();
        return coinflipPending.get(target.getUuid());
    }

    // =====================================================================
    // === Load / Save =====================================================
    // =====================================================================

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Double> e : map.entrySet()) {
                        balances.put(e.getKey(), Math.min(e.getValue().longValue(), MAX));
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public void save() {
        try {
            Map<UUID, Long> data = new HashMap<>(balances);
            String json = GSON.toJson(data, TYPE);
            Files.writeString(file, json);
        } catch (IOException ignored) {}

        try {
            String json = GSON.toJson(lastDaily, new TypeToken<Map<UUID, Long>>(){}.getType());
            Files.writeString(dailyFile, json);
        } catch (IOException ignored) {}

        try {
            String json = GSON.toJson(dailySells, DAILY_SELL_TYPE);
            Files.writeString(dailySellFile, json);
        } catch (IOException ignored) {}

        saveShards();
    }

    private void loadDaily() {
        if (Files.exists(dailyFile)) {
            try {
                String json = Files.readString(dailyFile);
                Map<UUID, Long> map = GSON.fromJson(json, new TypeToken<Map<UUID, Long>>(){}.getType());
                if (map != null) lastDaily.putAll(map);
            } catch (IOException ignored) {}
        }
    }

    private void loadDailySells() {
        if (Files.exists(dailySellFile)) {
            try {
                String json = Files.readString(dailySellFile);
                Map<UUID, DailySellData> map = GSON.fromJson(json, DAILY_SELL_TYPE);
                if (map != null) dailySells.putAll(map);
            } catch (IOException ignored) {}
        }
    }

    // =====================================================================
    // === Scoreboard / Leaderboard =======================================
    // =====================================================================

    private void applyScoreboardSettingOnStartup() {
        Scoreboard board = server.getScoreboard();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
            return;
        }

        board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);

        ScoreboardObjective existing = board.getNullableObjective("eco_balance");
        if (existing != null) {
            board.removeObjective(existing);
        }

        objective = null;
        displayed.clear();
    }

    private void setupObjective() {
        Scoreboard board = server.getScoreboard();
        objective = board.getNullableObjective("eco_balance");

        if (objective == null) {
            objective = board.addObjective(
                    "eco_balance",
                    ScoreboardCriterion.DUMMY,
                    Text.literal("Balance"),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    null
            );
        }
        board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        if (!EconomyConfig.get().scoreboardEnabled) {
            Scoreboard board = server.getScoreboard();
            board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
            if (objective != null) board.removeObjective(objective);
            objective = null;
            displayed.clear();
            return;
        }

        Scoreboard board = server.getScoreboard();
        if (objective != null) board.removeObjective(objective);

        objective = board.addObjective(
                "eco_balance",
                ScoreboardCriterion.DUMMY,
                Text.literal("Balance"),
                ScoreboardCriterion.RenderType.INTEGER,
                true,
                null
        );
        board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        displayed.clear();

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(balances.entrySet());
        sorted.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            if (c != 0) return c;

            String an = resolveName(server, a.getKey());
            String bn = resolveName(server, b.getKey());
            c = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
            if (c != 0) return c;

            return a.getKey().compareTo(b.getKey());
        });

        for (var e : sorted.stream().limit(5).toList()) {
            UUID id = e.getKey();
            String name = resolveName(server, id);
            ScoreAccess access = board.getOrCreateScore(
                    ScoreHolder.fromName(name),
                    objective
            );
            access.setScore(e.getValue().intValue());
            displayed.add(id);
        }
    }

    public boolean toggleScoreboard() {
        Scoreboard board = server.getScoreboard();
        EconomyConfig.get().scoreboardEnabled = !EconomyConfig.get().scoreboardEnabled;
        EconomyConfig.save();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        } else {
            board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
            if (objective != null) {
                board.removeObjective(objective);
                objective = null;
            }
        }

        return EconomyConfig.get().scoreboardEnabled;
    }

    // =====================================================================
    // === Misc ============================================================
    // =====================================================================

    public com.palordersoftworks.economycraft.shop.ShopManager getShop() {
        return shop;
    }

    public com.palordersoftworks.economycraft.orders.OrderManager getOrders() {
        return orders;
    }

    public PriceRegistry getPrices() {
        return prices;
    }

    public com.palordersoftworks.economycraft.playervault.PlayerVaultManager getPlayerVaults() {
        return playerVaults;
    }

    public Map<UUID, Long> getBalances() {
        return balances;
    }

    public void removePlayer(UUID id) {
        balances.remove(id);
        shards.remove(id);
        playerVaults.removePlayer(id);
        updateLeaderboard();
        save();
    }

    public boolean claimDaily(UUID player) {
        long today = LocalDate.now().toEpochDay();
        long last = lastDaily.getOrDefault(player, -1L);
        if (last == today) return false;
        lastDaily.put(player, today);
        addMoney(player, EconomyConfig.get().dailyAmount);
        if (EconomyConfig.get().shardsEnabled && EconomyConfig.get().dailyShards > 0) {
            addShards(player, EconomyConfig.get().dailyShards);
        }
        return true;
    }

    public boolean tryRecordDailySell(UUID player, long saleAmount) {
        long limit = EconomyConfig.get().dailySellLimit;
        if (limit <= 0) return false;

        DailySellData data = getOrCreateTodaySellData(player);
        long newTotal = data.amount() + saleAmount;
        if (newTotal > limit) {
            return true;
        }

        dailySells.put(player, new DailySellData(data.day(), newTotal));
        return false;
    }

    public long getDailySellRemaining(UUID player) {
        long limit = EconomyConfig.get().dailySellLimit;
        if (limit <= 0) return Long.MAX_VALUE;

        DailySellData data = getOrCreateTodaySellData(player);
        return Math.max(0, limit - data.amount());
    }

    private DailySellData getOrCreateTodaySellData(UUID player) {
        long today = LocalDate.now().toEpochDay();
        DailySellData data = dailySells.get(player);
        if (data == null || data.day() != today) {
            data = new DailySellData(today, 0L);
            dailySells.put(player, data);
        }
        return data;
    }

    public void handlePvpKill(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        if (victim == null || killer == null) return;
        if (victim.getUuid().equals(killer.getUuid())) return;

        double pct = EconomyConfig.get().pvpBalanceLossPercentage;
        if (pct > 0.0) {
            long victimBal = getBalance(victim.getUuid(), true);
            if (victimBal > 0L) {
                long loss = (long) Math.floor(pct * victimBal);
                if (loss > 0L) {
                    removeMoney(victim.getUuid(), loss);
                    addMoney(killer.getUuid(), loss);

                    victim.sendMessage(Text.literal(
                                    "You lost " + EconomyCraft.formatMoney(loss) + " for being killed by " + killer.getName().getString())
                            .formatted(net.minecraft.util.Formatting.RED));

                    killer.sendMessage(Text.literal(
                                    "You received " + EconomyCraft.formatMoney(loss) + " for killing " + victim.getName().getString())
                            .formatted(net.minecraft.util.Formatting.GREEN));
                }
            }
        }

        if (EconomyConfig.get().shardsEnabled && EconomyConfig.get().shardsPerPlayerKill > 0) {
            addShards(killer.getUuid(), EconomyConfig.get().shardsPerPlayerKill);
            killer.sendMessage(Text.literal("+" + EconomyConfig.get().shardsPerPlayerKill + " shards")
                    .formatted(net.minecraft.util.Formatting.LIGHT_PURPLE));
        }
    }

    private long clamp(long value) {
        return Math.max(0, Math.min(MAX, value));
    }

    private static final class UserCacheEntry {
        String name;
        String uuid;
    }

    private record DailySellData(long day, long amount) {}
}
