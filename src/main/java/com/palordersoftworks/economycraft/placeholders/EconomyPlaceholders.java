package com.palordersoftworks.economycraft.placeholders;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EconomyPlaceholders {
    private static final Pattern TOKEN = Pattern.compile("%economycraft_([a-z0-9_]+)%", Pattern.CASE_INSENSITIVE);
    private static final NumberFormat INTEGER = NumberFormat.getIntegerInstance(Locale.US);

    private EconomyPlaceholders() {}

    public static void tryRegister() {
    }

    public static String replace(String input, ServerPlayerEntity player) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = TOKEN.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = resolve(matcher.group(1), player);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String resolve(String key, ServerPlayerEntity player) {
        if (key == null || key.isBlank()) {
            return "";
        }

        EconomyManager manager = player != null
                ? EconomyCraft.getManager(player.getEntityWorld().getServer())
                : null;

        String normalized = key.trim().toLowerCase(Locale.ROOT);

        if ("balance".equals(normalized)) {
            return manager != null ? Long.toString(manager.getBalance(player.getUuid(), true)) : "0";
        }
        if ("balance_formatted".equals(normalized)) {
            return manager != null ? EconomyCraft.formatMoney(manager.getBalance(player.getUuid(), true)) : EconomyCraft.formatMoney(0L);
        }
        if ("shards".equals(normalized)) {
            return manager != null ? Long.toString(manager.getShards(player.getUuid(), true)) : "0";
        }
        if ("shards_formatted".equals(normalized)) {
            return manager != null ? EconomyCraft.formatShards(manager.getShards(player.getUuid(), true)) : EconomyCraft.formatShards(0L);
        }
        if ("daily_sell_remaining".equals(normalized)) {
            return manager != null ? Long.toString(manager.getDailySellRemaining(player.getUuid())) : "0";
        }
        if ("daily_sell_limit".equals(normalized)) {
            return Long.toString(EconomyConfig.get().dailySellLimit);
        }
        if ("tax_rate".equals(normalized)) {
            return formatPercent(EconomyConfig.get().taxRate);
        }
        if ("server_total_balance".equals(normalized)) {
            return manager != null ? Long.toString(totalBalance(manager)) : "0";
        }
        if ("balance_rank".equals(normalized)) {
            return manager != null ? Long.toString(balanceRank(manager, player.getUuid())) : "0";
        }
        if ("player_vault_amount".equals(normalized)) {
            return manager != null ? Long.toString(resolveVaultAmount(manager, player.getUuid())) : Long.toString(EconomyConfig.get().playerVaultDefaultAmount);
        }
        if ("player_vault_rows".equals(normalized)) {
            return Integer.toString(EconomyConfig.get().playerVaultRows);
        }
        if ("player_vault_max_amount".equals(normalized)) {
            return Integer.toString(EconomyConfig.get().playerVaultMaxAmount);
        }
        if ("player_vault_enabled".equals(normalized)) {
            return Boolean.toString(EconomyConfig.get().playerVaultEnabled);
        }

        return "";
    }

    public static String resolve(String key, MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return "";
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            return resolve(key, player);
        }
        EconomyManager manager = EconomyCraft.getManager(server);
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if ("balance".equals(normalized)) {
            Long bal = manager.getBalance(playerId, false);
            return bal == null ? "0" : Long.toString(bal);
        }
        if ("balance_formatted".equals(normalized)) {
            Long bal = manager.getBalance(playerId, false);
            return bal == null ? EconomyCraft.formatMoney(0L) : EconomyCraft.formatMoney(bal);
        }
        if ("shards".equals(normalized)) {
            return Long.toString(manager.getShards(playerId, false));
        }
        if ("shards_formatted".equals(normalized)) {
            return EconomyCraft.formatShards(manager.getShards(playerId, false));
        }
        if ("daily_sell_remaining".equals(normalized)) {
            return Long.toString(manager.getDailySellRemaining(playerId));
        }
        if ("server_total_balance".equals(normalized)) {
            return Long.toString(totalBalance(manager));
        }
        if ("balance_rank".equals(normalized)) {
            return Long.toString(balanceRank(manager, playerId));
        }
        if ("player_vault_amount".equals(normalized)) {
            return Long.toString(resolveVaultAmount(manager, playerId));
        }
        if ("player_vault_rows".equals(normalized)) {
            return Integer.toString(EconomyConfig.get().playerVaultRows);
        }
        if ("player_vault_max_amount".equals(normalized)) {
            return Integer.toString(EconomyConfig.get().playerVaultMaxAmount);
        }
        if ("player_vault_enabled".equals(normalized)) {
            return Boolean.toString(EconomyConfig.get().playerVaultEnabled);
        }
        if ("daily_sell_limit".equals(normalized)) {
            return Long.toString(EconomyConfig.get().dailySellLimit);
        }
        if ("tax_rate".equals(normalized)) {
            return formatPercent(EconomyConfig.get().taxRate);
        }
        return "";
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
        if (playerId == null) {
            return 0L;
        }
        Long current = manager.getBalances().get(playerId);
        if (current == null) {
            return 0L;
        }

        long rank = 1L;
        for (Map.Entry<UUID, Long> entry : manager.getBalances().entrySet()) {
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
                    Method method = vaults.getClass().getMethod(methodName, UUID.class);
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
        double pct = Math.max(0.0, value) * 100.0;
        return INTEGER.format(Math.round(pct)) + "%";
    }
}