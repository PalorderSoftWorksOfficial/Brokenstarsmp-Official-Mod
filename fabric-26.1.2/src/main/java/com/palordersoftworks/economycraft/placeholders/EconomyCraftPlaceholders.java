package com.palordersoftworks.economycraft.placeholders;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.IdentifierCompat;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;

public final class EconomyCraftPlaceholders {
    private EconomyCraftPlaceholders() {}

    public static void register() {
        registerServer("balance", (ctx, arg) -> playerValue(ctx, player -> EconomyCraft.formatMoney(balance(player))));
        registerServer("balance_formatted", (ctx, arg) -> playerValue(ctx, player -> EconomyCraft.formatMoney(balance(player))));
        registerServer("shards", (ctx, arg) -> playerValue(ctx, player -> EconomyCraft.formatShards(shards(player))));
        registerServer("shards_formatted", (ctx, arg) -> playerValue(ctx, player -> EconomyCraft.formatShards(shards(player))));
        registerServer("daily_sell_remaining", (ctx, arg) -> playerValue(ctx, player -> Long.toString(manager(player).getDailySellRemaining(player.getUuid()))));
        registerServer("daily_sell_limit", (ctx, arg) -> PlaceholderResult.value(Text.literal(Long.toString(EconomyConfig.get().dailySellLimit))));
        registerServer("tax_rate", (ctx, arg) -> PlaceholderResult.value(Text.literal(formatPercent(EconomyConfig.get().taxRate))));
        registerServer("server_total_balance", (ctx, arg) -> playerValue(ctx, player -> Long.toString(totalBalance(manager(player)))));
        registerServer("balance_rank", (ctx, arg) -> playerValue(ctx, player -> Long.toString(balanceRank(manager(player), player.getUuid()))));
        registerServer("player_vault_amount", (ctx, arg) -> playerValue(ctx, player -> Long.toString(resolveVaultAmount(manager(player), player.getUuid()))));
        registerServer("player_vault_rows", (ctx, arg) -> PlaceholderResult.value(Text.literal(Integer.toString(EconomyConfig.get().playerVaultRows))));
        registerServer("player_vault_max_amount", (ctx, arg) -> PlaceholderResult.value(Text.literal(Integer.toString(EconomyConfig.get().playerVaultMaxAmount))));
        registerServer("player_vault_enabled", (ctx, arg) -> PlaceholderResult.value(Text.literal(Boolean.toString(EconomyConfig.get().playerVaultEnabled))));
    }

    private static void registerServer(String path, BiFunction<ServerPlaceholderContext, String, PlaceholderResult> handler) {
        var id = IdentifierCompat.unwrap(IdentifierCompat.fromNamespaceAndPath(EconomyCraft.MOD_ID, path));
        if (id != null) {
            Placeholders.registerServer(id, handler::apply);
        }
    }

    private static PlaceholderResult playerValue(ServerPlaceholderContext ctx, PlayerValueResolver resolver) {
        if (!ctx.hasServerPlayer() || ctx.serverPlayer() == null) {
            return PlaceholderResult.invalid("No player context");
        }
        ServerPlayerEntity player = ctx.serverPlayer();
        return PlaceholderResult.value(Text.literal(resolver.resolve(player)));
    }

    private static EconomyManager manager(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        return EconomyCraft.getManager(server);
    }

    private static long balance(ServerPlayerEntity player) {
        return manager(player).getBalance(player.getUuid(), true);
    }

    private static long shards(ServerPlayerEntity player) {
        return manager(player).getShards(player.getUuid(), true);
    }

    private static long totalBalance(EconomyManager manager) {
        long total = 0L;
        for (Long value : manager.getBalances().values()) {
            if (value != null && value > 0L) {
                total += value;
            }
        }
        return total;
    }

    private static long balanceRank(EconomyManager manager, UUID playerId) {
        Long current = manager.getBalances().get(playerId);
        if (current == null) {
            return 0L;
        }
        long rank = 1L;
        for (var entry : manager.getBalances().entrySet()) {
            Long value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value > current) {
                rank++;
            } else if (value.equals(current) && !entry.getKey().equals(playerId) && entry.getKey().compareTo(playerId) < 0) {
                rank++;
            }
        }
        return rank;
    }

    private static long resolveVaultAmount(EconomyManager manager, UUID playerId) {
        try {
            Object vaults = manager.getPlayerVaults();
            if (vaults == null) {
                return EconomyConfig.get().playerVaultDefaultAmount;
            }
            for (String methodName : new String[] {
                    "getVaultAmount",
                    "getVaultCount",
                    "getPlayerVaultAmount",
                    "getAvailableVaults",
                    "getUnlockedVaultCount"
            }) {
                try {
                    var method = vaults.getClass().getMethod(methodName, UUID.class);
                    Object result = method.invoke(vaults, playerId);
                    if (result instanceof Number number) {
                        return Math.max(0L, number.longValue());
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return EconomyConfig.get().playerVaultDefaultAmount;
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0, value) * 100.0);
    }

    @FunctionalInterface
    private interface PlayerValueResolver {
        String resolve(ServerPlayerEntity player);
    }
}
