package com.palordersoftworks.brokenstarsmpmod.economy.playervault;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtrasConfig;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerVaultCommands {
    public static final String PERMISSION_NODE = "brokenstarsmp.economy.playervault";

    private PlayerVaultCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var node = dispatcher.register(Commands.literal("playervault")
                .requires(PlayerVaultCommands::mayUse)
                .executes(ctx -> openPicker(ctx.getSource()))
                .then(Commands.literal("rename")
                        .then(Commands.argument("vault", IntegerArgumentType.integer(1))
                                .suggests(PlayerVaultCommands::suggestVaultNumbers)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> renameVault(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "vault"),
                                                StringArgumentType.getString(ctx, "name")
                                        )))))
                .then(Commands.argument("vault", IntegerArgumentType.integer(1))
                        .suggests(PlayerVaultCommands::suggestVaultNumbers)
                        .executes(ctx -> openVault(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "vault")
                        ))));
        dispatcher.register(Commands.literal("pv").requires(PlayerVaultCommands::mayUse).redirect(node));
    }

    public static boolean mayUse(CommandSourceStack source) {
        if (!EconomyExtrasConfig.get().playerVaultEnabled) return false;
        if (!EconomyExtrasConfig.get().playerVaultRequirePermission) return true;
        // Soft gate when requirePermission is on: allow console and permission-level players via PermissionCompat.
        return source.getPlayer() == null || PermissionCompat.gamemaster().test(source);
    }

    private static CompletableFuture<Suggestions> suggestVaultNumbers(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
            return Suggestions.empty();
        }
        var vaults = EconomyExtras.getVaults(ctx.getSource().getServer());
        int max = resolveMaxVaults(p);
        int unlocked = vaults.getUnlockedVaultCount(p.getUUID(), max);
        String prefix = builder.getRemaining().toLowerCase();
        for (int i = 1; i <= unlocked; i++) {
            String s = String.valueOf(i);
            if (prefix.isEmpty() || s.startsWith(prefix)) builder.suggest(s);
        }
        return builder.buildFuture();
    }

    private static int openPicker(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }
        if (resolveMaxVaults(player) <= 0) {
            source.sendFailure(Component.literal("Player vaults are disabled for you."));
            return 0;
        }
        PlayerVaultPickerUi.open(player);
        return 1;
    }

    private static int openVault(CommandSourceStack source, int index) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }
        int max = resolveMaxVaults(player);
        if (max <= 0) {
            source.sendFailure(Component.literal("Player vaults are disabled for you."));
            return 0;
        }
        if (index > max) {
            source.sendFailure(Component.literal("You do not have access to vault #" + index + ". Maximum: " + max)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        var vaults = EconomyExtras.getVaults(source.getServer());
        int unlocked = vaults.getUnlockedVaultCount(player.getUUID(), max);
        if (index > unlocked) {
            source.sendFailure(Component.literal(
                    "Vault #" + index + " is locked. Open /pv to unlock (current: " + unlocked + "/" + max + ").")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        PlayerVaultUi.open(player, index);
        return 1;
    }

    private static int renameVault(CommandSourceStack source, int index, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }
        int max = resolveMaxVaults(player);
        var vaults = EconomyExtras.getVaults(source.getServer());
        int unlocked = vaults.getUnlockedVaultCount(player.getUUID(), max);
        if (index < 1 || index > unlocked) {
            source.sendFailure(Component.literal("That vault is locked or invalid.").withStyle(ChatFormatting.RED));
            return 0;
        }
        vaults.setVaultName(player.getUUID(), index, name);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            player.sendSystemMessage(Component.literal("Cleared name for Vault #" + index + ".")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);
            player.sendSystemMessage(Component.literal("Named Vault #" + index + " to \"" + trimmed + "\".")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    public static int resolveMaxVaults(ServerPlayer player) {
        EconomyExtrasConfig cfg = EconomyExtrasConfig.get();
        int def = Math.max(0, cfg.playerVaultDefaultAmount);
        int cap = Math.max(1, cfg.playerVaultMaxAmount);
        return Math.min(cap, def);
    }
}
