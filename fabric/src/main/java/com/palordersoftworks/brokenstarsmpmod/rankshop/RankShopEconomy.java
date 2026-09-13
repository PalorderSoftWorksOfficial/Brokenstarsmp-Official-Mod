package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Small abstraction over EconomyCraft 1.9.0 for the Rank Shop.
 *
 * Backed by the real API ({@code EconomyCraft.getManager}, {@code getBalance},
 * {@code removeMoney}/{@code addMoney}, {@code EconomyCraft.formatMoney}) so shop
 * money formatting matches the rest of the server. EconomyCraft balances are
 * {@code long}-backed, which is why every money path here uses {@code long}.
 */
public final class RankShopEconomy {
    private RankShopEconomy() {}

    private static EconomyManager economy(MinecraftServer server) {
        return EconomyCraft.getManager(server);
    }

    /** @return the player's EconomyCraft balance, or 0 when unknown. */
    public static long getMoney(MinecraftServer server, UUID player) {
        Long balance = economy(server).getBalance(player, false);
        return balance == null ? 0L : balance;
    }

    /**
     * Atomically removes money. The EconomyCraft implementation validates the
     * balance server-side and refuses the operation when it is too low.
     *
     * @return true when the money was removed
     */
    public static boolean withdrawMoney(MinecraftServer server, UUID player, long amount) {
        if (amount <= 0) {
            return false;
        }
        return economy(server).removeMoney(player, amount);
    }

    public static void depositMoney(MinecraftServer server, UUID player, long amount) {
        if (amount > 0) {
            economy(server).addMoney(player, amount);
        }
    }

    /** Formats money with EconomyCraft's own formatter (e.g. "$1.2B"). */
    public static String formatMoney(long amount) {
        return EconomyCraft.formatMoney(amount);
    }
}
