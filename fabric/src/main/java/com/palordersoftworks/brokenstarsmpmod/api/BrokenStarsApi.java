package com.palordersoftworks.brokenstarsmpmod.api;

import com.palordersoftworks.brokenstarsmpmod.rankshop.RankShopEconomy;
import com.palordersoftworks.brokenstarsmpmod.rankshop.RankShopService;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public integration surface for other mods on this server.
 *
 * <p>Everything here is safe to call from other mods at any time while the server
 * is running: values that depend on server-local stores (Rank Tokens) return
 * neutral defaults instead of throwing when the store is not ready.
 *
 * <pre>{@code
 * long balance = BrokenStarsApi.money(server, uuid);
 * BrokenStarsApi.giveTokens(uuid, 5);
 * Optional<BrokenStarsApi.TeamInfo> team = BrokenStarsApi.teamOf(uuid);
 * }</pre>
 */
public final class BrokenStarsApi {
    private BrokenStarsApi() {}

    /** Snapshot of a team's membership. */
    public record TeamInfo(String name, UUID owner, Set<UUID> members, String color) {}

    // ---- money (EconomyCraft) --------------------------------------------------

    /** @return the player's EconomyCraft balance, or 0 when unknown. */
    public static long money(MinecraftServer server, UUID player) {
        return RankShopEconomy.getMoney(server, player);
    }

    /**
     * Atomically removes money. The EconomyCraft implementation validates the
     * balance server-side and refuses the operation when it is too low.
     *
     * @return true when the money was removed
     */
    public static boolean removeMoney(MinecraftServer server, UUID player, long amount) {
        return RankShopEconomy.withdrawMoney(server, player, amount);
    }

    public static void addMoney(MinecraftServer server, UUID player, long amount) {
        RankShopEconomy.depositMoney(server, player, amount);
    }

    /** Formats money with EconomyCraft's own formatter (e.g. {@code "$1.2B"}). */
    public static String formatMoney(long amount) {
        return RankShopEconomy.formatMoney(amount);
    }

    // ---- rank tokens -------------------------------------------------------------

    /** @return the player's Rank Token balance, or 0 when the shop is not ready. */
    public static long tokens(UUID player) {
        try {
            return RankShopService.store().getBalance(player);
        } catch (IllegalStateException notReady) {
            return 0L;
        }
    }

    public static void giveTokens(UUID player, long amount) {
        try {
            RankShopService.giveTokens(player, amount);
        } catch (IllegalStateException notReady) {
            // Shop not initialized; nothing to credit.
        }
    }

    /** @return true when the player held at least {@code amount} tokens, which are then removed. */
    public static boolean takeTokens(UUID player, long amount) {
        try {
            return RankShopService.takeTokens(player, amount);
        } catch (IllegalStateException notReady) {
            return false;
        }
    }

    /** @return true when the balance was set (never below zero). */
    public static boolean setTokens(UUID player, long amount) {
        try {
            return RankShopService.setTokens(player, amount);
        } catch (IllegalStateException notReady) {
            return false;
        }
    }

    // ---- teams -------------------------------------------------------------------

    /** @return the player's team, or empty when they are not on one. */
    public static Optional<TeamInfo> teamOf(UUID player) {
        TeamManager.TeamData team = TeamManager.getTeamOf(player);
        return Optional.ofNullable(team).map(BrokenStarsApi::toInfo);
    }

    /** @return true when the two players are distinct members of the same team. */
    public static boolean sameTeam(UUID a, UUID b) {
        return TeamManager.isSameTeam(a, b);
    }

    /** @return every registered team, as immutable snapshots. */
    public static List<TeamInfo> teams() {
        return TeamManager.allTeams().stream()
                .map(BrokenStarsApi::toInfo)
                .collect(Collectors.toUnmodifiableList());
    }

    private static TeamInfo toInfo(TeamManager.TeamData team) {
        return new TeamInfo(team.name(), team.owner(), Set.copyOf(team.members()), team.color());
    }
}
