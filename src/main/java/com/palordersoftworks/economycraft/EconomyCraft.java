package com.palordersoftworks.economycraft;

import com.palordersoftworks.economycraft.playervault.PlayerVaultCommands;
import com.palordersoftworks.economycraft.util.ChatCompat;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.NumberFormat;
import java.util.Locale;

public final class EconomyCraft {
    public static final String MOD_ID = "economycraft";
    private static EconomyManager manager;
    private static MinecraftServer lastServer;
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(EconomyConfig::load);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher);
            EconomyCommands.registerExtraStandaloneAliases(dispatcher);
            if (EconomyConfig.get().standaloneCommands) {
                PlayerVaultCommands.registerStandalone(dispatcher);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0 && manager != null && server == lastServer) {
                manager.purgeExpiredCoinflips();
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(EconomyCraft::getManager);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null && lastServer == server) {
                manager.save();
                manager.getPlayerVaults().save();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player, server));
    }

    private static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        EconomyManager eco = getManager(server);
        eco.getBalance(player.getUuid(), true);

        if (eco.getOrders().hasDeliveries(player.getUuid()) || eco.getShop().hasDeliveries(player.getUuid())) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");

            if (ev != null) {
                Text msg = Text.literal("You have unclaimed items: ")
                        .formatted(Formatting.YELLOW)
                        .append(Text.literal("[Claim]")
                                .styled(s -> s.withUnderline(true).withColor(Formatting.GREEN).withClickEvent(ev)));
                player.sendMessage(msg);
            } else {
                ChatCompat.sendRunCommandTellraw(
                        player,
                        "You have unclaimed items: ",
                        "[Claim]",
                        "/eco orders claim"
                );
            }
        }
    }

    public static EconomyManager getManager(MinecraftServer server) {
        if (manager == null || lastServer != server) {
            manager = new EconomyManager(server);
            lastServer = server;
        }
        return manager;
    }

    public static Text createBalanceTitle(String baseTitle, ServerPlayerEntity player) {
        EconomyManager eco = getManager(player.getEntityWorld().getServer());
        long balance = eco.getBalance(player.getUuid(), true);
        return Text.literal(baseTitle + " - Balance: " + formatMoney(balance));
    }

    public static String formatMoney(long amount) {
        if (EconomyConfig.get().compactMoneyDisplay) {
            return formatMoneyCompact(amount);
        }
        return "$" + FORMAT.format(amount);
    }

    public static String formatShards(long amount) {
        return FORMAT.format(amount) + " shards";
    }

    private static String formatMoneyCompact(long n) {
        if (n >= 1_000_000_000L) {
            return "$" + trimTrailingZeros(String.format(Locale.US, "%.2fB", n / 1_000_000_000.0));
        }
        if (n >= 1_000_000L) {
            return "$" + trimTrailingZeros(String.format(Locale.US, "%.2fM", n / 1_000_000.0));
        }
        if (n >= 10_000L) {
            return "$" + trimTrailingZeros(String.format(Locale.US, "%.1fk", n / 1_000.0));
        }
        return "$" + FORMAT.format(n);
    }

    private static String trimTrailingZeros(String s) {
        if (!s.contains(".")) return s;
        String t = s.replaceAll("0+$", "");
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }
}
