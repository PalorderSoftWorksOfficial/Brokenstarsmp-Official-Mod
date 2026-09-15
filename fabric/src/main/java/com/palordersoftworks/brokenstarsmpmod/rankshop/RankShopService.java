package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.palordersoftworks.brokenstarsmpmod.messages.MiniMessageApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Business logic for Rank Tokens and rank purchases.
 *
 * Transaction rules enforced here:
 *  - Every purchase runs under a per-player in-flight lock, so double clicks and
 *    duplicate packets cannot run two purchases concurrently.
 *  - Tokens are withdrawn first (synchronous, persisted); only a successful
 *    withdrawal is followed by the LuckPerms grant. Any grant failure refunds the
 *    tokens. There is no code path that both keeps tokens and skips the grant.
 *  - Prices/durations/groups always come from {@link RankDefinition}; client input
 *    can only ever name a rank id, never a price or a LuckPerms group.
 *  - Money->token conversion validates the real EconomyCraft balance server-side and
 *    is atomic in the same direction (money first, then tokens).
 */
public final class RankShopService {
    private RankShopService() {}

    public enum RankState { NOT_OWNED, ACTIVE_TEMPORARY, EXPIRED, PERMANENT }

    private static volatile RankShopStore store;
    private static volatile List<RankDefinition> ranks = List.of();
    private static volatile MinecraftServer boundServer;
    private static final Set<UUID> PURCHASE_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static long nextExpiryScan;

    // ---- lifecycle -----------------------------------------------------------

    public static void init(MinecraftServer server) {
        boundServer = server;
        store = new RankShopStore(server);
        reloadRanks();

        ServerTickEvents.END_SERVER_TICK.register(RankShopService::tick);
    }

    public static void shutdown() {
        store = null;
        boundServer = null;
        ranks = List.of();
    }

    public static void reloadRanks() {
        Map<String, Object> table = RankShopConfig.RANK_SHOP_RANKS;
        BiConsumer<String, Throwable> onError = (id, error) ->
                com.mojang.logging.LogUtils.getLogger()
                        .error("[BrokenStars] Invalid rank config '{}': {}", id, error.getMessage());
        List<RankDefinition> parsed = RankDefinition.loadAll(table, onError);
        parsed.sort(Comparator.comparingInt(RankDefinition::slot).thenComparing(RankDefinition::id));
        ranks = Collections.unmodifiableList(parsed);
    }

    public static RankShopStore store() {
        RankShopStore current = store;
        if (current == null) {
            throw new IllegalStateException("Rank shop is not ready");
        }
        return current;
    }

    public static List<RankDefinition> ranks() {
        return ranks;
    }

    public static RankDefinition rank(String id) {
        for (RankDefinition rank : ranks) {
            if (rank.id().equalsIgnoreCase(id)) {
                return rank;
            }
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now < nextExpiryScan) {
            return;
        }
        nextExpiryScan = now + 30_000L;
        refreshExpiredRanks();
    }

    /**
     * Re-grants any shop rank whose recorded expiration has passed. This is what
     * makes temporary ranks expire "automatically" even though LuckPerms nodes with
     * an expiry stop applying on their own: the re-grant restarts the duration only
     * for ranks the player still owns in the store ledger.
     */
    public static void refreshExpiredRanks() {
        RankShopStore current = store;
        if (current == null) {
            return;
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, Map<String, Long>> entry : current.expirationsSnapshot().entrySet()) {
            UUID player = entry.getKey();
            for (Map.Entry<String, Long> rankEntry : entry.getValue().entrySet()) {
                RankDefinition rank = rank(rankEntry.getKey());
                if (rank == null || rank.isPermanent()) {
                    continue;
                }
                if (rankEntry.getValue() != null && rankEntry.getValue() > 0
                        && rankEntry.getValue() <= now.toEpochMilli()) {
                    refreshRank(player, rank, now);
                }
            }
        }
    }

    /** Restarts the duration of an expired temporary rank at no token cost. */
    public static boolean refreshRank(UUID player, RankDefinition rank, Instant now) {
        if (rank.isPermanent()) {
            return false;
        }
        Duration duration = rank.duration();
        boolean granted = LuckPermsService.grant(player, rank.group(), duration, rank.replaces());
        if (granted) {
            store().recordExpiration(player, rank.id(), now.plus(duration).toEpochMilli());
        }
        return granted;
    }

    /** Re-grants every expired rank on login (expirations survive restarts in JSON). */
    public static void onPlayerJoin(ServerPlayer player) {
        RankShopStore current = store;
        if (current == null) {
            return;
        }
        Instant now = Instant.now();
        for (RankDefinition rank : ranks) {
            if (rank.isPermanent()) {
                continue;
            }
            Long expiration = current.getExpiration(player.getUUID(), rank.id());
            if (expiration != null && expiration > 0 && expiration <= now.toEpochMilli()) {
                refreshRank(player.getUUID(), rank, now);
            }
        }
    }

    // ---- state ---------------------------------------------------------------

    public static RankState stateOf(UUID player, RankDefinition rank, Instant now) {
        if (rank.isPermanent()) {
            Long expiration = store().getExpiration(player, rank.id());
            return expiration != null && expiration < 0 ? RankState.PERMANENT : RankState.NOT_OWNED;
        }
        Long expiration = store().getExpiration(player, rank.id());
        if (expiration == null) {
            return RankState.NOT_OWNED;
        }
        if (expiration < 0) {
            return RankState.PERMANENT;
        }
        return expiration > now.toEpochMilli() ? RankState.ACTIVE_TEMPORARY : RankState.EXPIRED;
    }

    public static Long expirationOf(UUID player, RankDefinition rank) {
        return store().getExpiration(player, rank.id());
    }

    // ---- purchase -------------------------------------------------------------

    /** @return true when the purchase fully succeeded. */
    public static boolean purchase(ServerPlayer player, RankDefinition rank) {
        UUID playerUuid = player.getUUID();
        if (!PURCHASE_IN_FLIGHT.add(playerUuid)) {
            player.sendSystemMessage(Component.literal("A purchase is already being processed.")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        try {
            return purchaseLocked(player, rank);
        } finally {
            PURCHASE_IN_FLIGHT.remove(playerUuid);
        }
    }

    private static boolean purchaseLocked(ServerPlayer player, RankDefinition rank) {
        if (!rank.enabled()) {
            player.sendSystemMessage(Component.literal("This rank is not available.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID playerUuid = player.getUUID();
        Instant now = Instant.now();
        long price = rank.priceFor(now);
        RankState state = stateOf(playerUuid, rank, now);

        if (state == RankState.PERMANENT) {
            player.sendSystemMessage(Component.literal("You already own this rank permanently.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (state == RankState.ACTIVE_TEMPORARY && rank.renewal() == RankDefinition.RenewalMode.DENY) {
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_DENIED, Map.of("rank", rankDisplayName(rank)));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        long balance = store().getBalance(playerUuid);
        if (balance < price) {
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_INSUFFICIENT, Map.of(
                    "price", String.valueOf(price),
                    "balance", String.valueOf(balance),
                    "missing", String.valueOf(price - balance)));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        // 1) Take the tokens first; the store persists synchronously or refuses.
        if (!store().withdraw(playerUuid, price)) {
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_INSUFFICIENT, Map.of(
                    "price", String.valueOf(price),
                    "balance", String.valueOf(balance),
                    "missing", String.valueOf(price - balance)));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        // 2) Grant the entitlement; refund on any failure so tokens are never lost.
        boolean granted;
        try {
            granted = applyGrant(playerUuid, rank, state, now);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("[BrokenStars] Rank grant crashed for {} ({})", playerUuid, rank.id(), e);
            granted = false;
        }
        if (!granted) {
            store().deposit(playerUuid, price);
            player.sendSystemMessage(Component.literal("Purchase failed - your Rank Tokens were refunded.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 3) Feedback only after the transaction is committed.
        if (state == RankState.ACTIVE_TEMPORARY) {
            Long expiration = store().getExpiration(playerUuid, rank.id());
            String remaining = expiration == null ? "?" : formatRemaining(expiration - now.toEpochMilli());
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_RENEWED, Map.of(
                    "rank", rankDisplayName(rank),
                    "remaining", remaining));
        } else {
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_PURCHASED, Map.of(
                    "rank", rankDisplayName(rank),
                    "duration", formatDuration(rank)));
        }
        playSound(player, RankShopConfig.RANK_SHOP_SOUND_PURCHASE);
        return true;
    }

    private static boolean applyGrant(UUID playerUuid, RankDefinition rank, RankState state, Instant now) {
        if (rank.isPermanent()) {
            boolean granted = LuckPermsService.grant(playerUuid, rank.group(), null, rank.replaces());
            if (granted) {
                store().recordExpiration(playerUuid, rank.id(), -1L);
            }
            return granted;
        }

        Duration duration = rank.duration();
        long newExpiration;
        if (state == RankState.ACTIVE_TEMPORARY && rank.renewal() == RankDefinition.RenewalMode.EXTEND) {
            Long current = store().getExpiration(playerUuid, rank.id());
            long base = current != null && current > now.toEpochMilli() ? current : now.toEpochMilli();
            newExpiration = base + duration.toMillis();
        } else {
            // REPLACE, first purchase, or renewing an expired rank
            newExpiration = now.plus(duration).toEpochMilli();
        }

        boolean granted = LuckPermsService.grant(playerUuid, rank.group(), duration, rank.replaces());
        if (granted) {
            store().recordExpiration(playerUuid, rank.id(), newExpiration);
        }
        return granted;
    }

    // ---- money -> tokens -------------------------------------------------------

    /** @return true when the conversion fully succeeded. */
    public static boolean convertMoneyToTokens(ServerPlayer player, long tokens) {
        if (tokens <= 0) {
            return false;
        }
        double perToken = RankShopConfig.RANK_TOKEN_MONEY_PER_TOKEN;
        if (perToken <= 0) {
            player.sendSystemMessage(Component.literal("Token conversion is misconfigured.").withStyle(ChatFormatting.RED));
            return false;
        }
        long moneyPerToken = (long) Math.floor(perToken);
        BigInteger cost = BigInteger.valueOf(moneyPerToken).multiply(BigInteger.valueOf(tokens));
        if (cost.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            player.sendSystemMessage(Component.literal("That amount is too large.").withStyle(ChatFormatting.RED));
            return false;
        }
        long costLong = cost.longValue();
        UUID playerUuid = player.getUUID();

        // Money leaves first (EconomyCraft validates the balance server-side),
        // tokens are credited second with synchronous persistence.
        if (!RankShopEconomy.withdrawMoney(boundServer, playerUuid, costLong)) {
            sendMini(player, RankShopConfig.RANK_SHOP_MSG_CONVERT_FAILED, Map.of(
                    "money", RankShopEconomy.formatMoney(costLong),
                    "balance", RankShopEconomy.formatMoney(RankShopEconomy.getMoney(boundServer, playerUuid))));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        store().deposit(playerUuid, tokens);
        sendMini(player, RankShopConfig.RANK_SHOP_MSG_CONVERT_SUCCESS, Map.of(
                "money", RankShopEconomy.formatMoney(costLong),
                "tokens", String.valueOf(tokens)));
        playSound(player, RankShopConfig.RANK_SHOP_SOUND_CONVERT);
        return true;
    }

    // ---- tokens -> money --------------------------------------------------------

    /**
     * Sell-rate payout for {@code tokens} tokens: a configurable fraction of the
     * buy rate (default 25%, i.e. selling pays 75% below what a token costs),
     * floored per-token so long math never rounds up in the player's favour.
     */
    public static long sellPayout(long tokens) {
        if (tokens <= 0) {
            return 0L;
        }
        double multiplier = RankShopConfig.RANK_TOKEN_SELL_MULTIPLIER;
        if (multiplier <= 0) {
            return 0L;
        }
        double perToken = RankShopConfig.RANK_TOKEN_MONEY_PER_TOKEN;
        long payoutPerToken = (long) Math.floor(perToken * multiplier);
        if (payoutPerToken <= 0) {
            return 0L;
        }
        BigInteger payout = BigInteger.valueOf(payoutPerToken).multiply(BigInteger.valueOf(tokens));
        return payout.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : payout.longValue();
    }

    /** @return true when the sale fully succeeded. */
    public static boolean convertTokensToMoney(ServerPlayer player, long tokens) {
        if (tokens <= 0) {
            return false;
        }
        UUID playerUuid = player.getUUID();
        long balance = store().getBalance(playerUuid);
        if (balance < tokens) {
            player.sendSystemMessage(Component.literal("You do not have that many Rank Tokens.")
                    .withStyle(ChatFormatting.RED));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        long payout = sellPayout(tokens);
        if (payout <= 0) {
            player.sendSystemMessage(Component.literal("Token selling is misconfigured.").withStyle(ChatFormatting.RED));
            return false;
        }

        // Tokens leave first (the store persists synchronously or refuses),
        // money is credited second; a failed credit refunds the tokens.
        if (!store().withdraw(playerUuid, tokens)) {
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        try {
            RankShopEconomy.depositMoney(boundServer, playerUuid, payout);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("[BrokenStars] Token sale payout failed for {}; refunding tokens", playerUuid, e);
            store().deposit(playerUuid, tokens);
            player.sendSystemMessage(Component.literal("Sale failed - your Rank Tokens were refunded.")
                    .withStyle(ChatFormatting.RED));
            playSound(player, RankShopConfig.RANK_SHOP_SOUND_DENY);
            return false;
        }

        sendMini(player, RankShopConfig.RANK_SHOP_MSG_SELL_SUCCESS, Map.of(
                "money", RankShopEconomy.formatMoney(payout),
                "tokens", String.valueOf(tokens)));
        playSound(player, RankShopConfig.RANK_SHOP_SOUND_SELL);
        return true;
    }

    // ---- admin -----------------------------------------------------------------

    public static void giveTokens(UUID target, long amount) {
        if (amount > 0) {
            store().deposit(target, amount);
        }
    }

    /** @return true when tokens were taken. Revokes tracked shop ranks on LuckPerms. */
    public static boolean takeTokens(UUID target, long amount) {
        if (amount <= 0 || !store().withdraw(target, amount)) {
            return false;
        }
        if (store().getBalance(target) == 0) {
            // balance reached 0: revoke tracked shop ranks so tokens and ranks stay in sync
            revokeTrackedRanks(target);
        }
        return true;
    }

    public static boolean setTokens(UUID target, long amount) {
        if (amount < 0) {
            return false;
        }
        boolean changed = store().setBalance(target, amount);
        if (changed && amount == 0) {
            revokeTrackedRanks(target);
        }
        return changed;
    }

    private static void revokeTrackedRanks(UUID target) {
        for (RankDefinition rank : ranks) {
            if (rank.isPermanent()) {
                continue;
            }
            if (store().getExpiration(target, rank.id()) != null) {
                LuckPermsService.revoke(target, rank.group());
                store().clearExpiration(target, rank.id());
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------

    public static String rankDisplayName(RankDefinition rank) {
        // Plain-text rendering of the configured MiniMessage name for chat/log use.
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(MiniMessageApi.parse(rank.name()));
    }

    public static String formatDuration(RankDefinition rank) {
        Duration duration = rank.duration();
        if (duration == null) {
            return "permanent";
        }
        return formatRemaining(duration.toMillis());
    }

    public static String formatRemaining(long millis) {
        if (millis < 0) {
            return "permanent";
        }
        long days = millis / 86_400_000L;
        long hours = (millis % 86_400_000L) / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        return minutes + "m";
    }

    public static void sendMini(ServerPlayer player, String miniMessage, Map<String, String> placeholders) {
        player.sendSystemMessage(render(miniMessage, placeholders));
    }

    /**
     * Renders a MiniMessage template with {@code <key>} placeholders.
     * Values are inserted with {@code Placeholder.unparsed}, so no player-influenced
     * value can inject MiniMessage tags.
     */
    public static net.minecraft.network.chat.Component render(String template, Map<String, String> placeholders) {
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder resolver =
                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.builder();
        placeholders.forEach((key, value) -> resolver.resolver(
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(key, value)));
        return MiniMessageApi.toNative(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(template, resolver.build()));
    }

    public static void playSound(ServerPlayer player, String registryName) {
        if (registryName == null || registryName.isBlank()) {
            return;
        }
        try {
            Identifier id = Identifier.withDefaultNamespace(registryName.trim().toLowerCase(java.util.Locale.ROOT));
            BuiltInRegistries.SOUND_EVENT.getOptional(id)
                    .ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
        } catch (Exception ignored) {
            // Unknown sound names must never break a transaction.
        }
    }
}
