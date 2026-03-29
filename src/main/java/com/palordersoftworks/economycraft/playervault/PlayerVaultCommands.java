package com.palordersoftworks.economycraft.playervault;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.util.FabricPermissionsCompat;
import com.palordersoftworks.economycraft.util.LuckPermsCompat;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PlayerVaultCommands {
    public static final String PERMISSION_NODE = "brokenstarsmp.economy.playervault";

    private PlayerVaultCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> ecoSubcommand() {
        return playervaultBranch("pv");
    }

    /** {@code /eco playervault [n]} — same behaviour as {@code /eco pv}. */
    public static LiteralArgumentBuilder<ServerCommandSource> ecoPlayervaultAlias() {
        return playervaultBranch("playervault");
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playervaultBranch(String name) {
        return literal(name)
                .requires(PlayerVaultCommands::mayUse)
                .executes(ctx -> openPicker(ctx.getSource()))
                .then(argument("vault", IntegerArgumentType.integer(1))
                        .suggests(PlayerVaultCommands::suggestVaultNumbers)
                        .executes(ctx -> openVault(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "vault")
                        )));
    }

    private static CompletableFuture<Suggestions> suggestVaultNumbers(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
            SuggestionsBuilder builder
    ) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity p)) {
            return Suggestions.empty();
        }
        var manager = EconomyCraft.getManager(ctx.getSource().getServer());
        int max = resolveMaxVaults(p);
        int unlocked = manager.getPlayerVaults().getUnlockedVaultCount(p.getUuid(), max);
        String prefix = builder.getRemaining().toLowerCase();
        for (int i = 1; i <= unlocked; i++) {
            String s = String.valueOf(i);
            if (prefix.isEmpty() || s.startsWith(prefix)) {
                builder.suggest(s);
            }
        }
        return builder.buildFuture();
    }

    /** Registers {@code /playervault} and {@code /pv} when standalone commands are enabled. */
    public static void registerStandalone(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> node = dispatcher.register(
                literal("playervault")
                        .requires(PlayerVaultCommands::mayUse)
                        .executes(ctx -> openPicker(ctx.getSource()))
                        .then(argument("vault", IntegerArgumentType.integer(1))
                                .suggests(PlayerVaultCommands::suggestVaultNumbers)
                                .executes(ctx -> openVault(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "vault")
                                )))
        );
        dispatcher.register(literal("pv").requires(PlayerVaultCommands::mayUse).redirect(node));
    }

    public static boolean mayUse(ServerCommandSource source) {
        if (!EconomyConfig.get().playerVaultEnabled) {
            return false;
        }
        if (!EconomyConfig.get().playerVaultRequirePermission) {
            return true;
        }
        return FabricPermissionsCompat.check(source, PERMISSION_NODE, false);
    }

    private static int openPicker(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Players only."));
            return 0;
        }
        int max = resolveMaxVaults(player);
        if (max <= 0) {
            source.sendError(Text.literal("Player vaults are disabled for you."));
            return 0;
        }
        try {
            PlayerVaultPickerUi.open(player, EconomyCraft.getManager(source.getServer()));
        } catch (Exception e) {
            source.sendError(Text.literal("Could not open vaults menu."));
            return 0;
        }
        return 1;
    }

    private static int openVault(ServerCommandSource source, int index) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Players only."));
            return 0;
        }
        int max = resolveMaxVaults(player);
        if (max <= 0) {
            source.sendError(Text.literal("Player vaults are disabled for you."));
            return 0;
        }
        if (index > max) {
            source.sendError(Text.literal("You do not have access to vault #" + index + ". Maximum: " + max)
                    .formatted(Formatting.RED));
            return 0;
        }
        var manager = EconomyCraft.getManager(source.getServer());
        int unlocked = manager.getPlayerVaults().getUnlockedVaultCount(player.getUuid(), max);
        if (index > unlocked) {
            source.sendError(Text.literal("Vault #" + index + " is locked. Open /pv and use Create Vault to unlock it "
                    + "(current: " + unlocked + "/" + max + ").").formatted(Formatting.YELLOW));
            return 0;
        }
        try {
            PlayerVaultUi.open(player, manager, index);
        } catch (Exception e) {
            source.sendError(Text.literal("Could not open vault."));
            return 0;
        }
        return 1;
    }

    /**
     * Max vault index allowed (inclusive). Uses LuckPerms meta {@link EconomyConfig#playerVaultLuckPermsMetaKey}
     * when set to a positive integer; otherwise {@link EconomyConfig#playerVaultDefaultAmount}.
     */
    public static int resolveMaxVaults(ServerPlayerEntity player) {
        EconomyConfig cfg = EconomyConfig.get();
        int def = Math.max(0, cfg.playerVaultDefaultAmount);
        int cap = Math.max(1, cfg.playerVaultMaxAmount);
        int fromMeta = -1;
        String key = cfg.playerVaultLuckPermsMetaKey;
        if (key == null || key.isBlank()) {
            key = "brokenstarsmp.economy.playervault.amount";
        }
        if (LuckPermsCompat.isLuckPermsPresent()) {
            var meta = LuckPermsCompat.getMetaValue(player.getUuid(), key);
            if (meta.isPresent()) {
                try {
                    fromMeta = Integer.parseInt(meta.get().trim());
                } catch (NumberFormatException ignored) {
                    fromMeta = -1;
                }
            }
        }
        int n = fromMeta > 0 ? fromMeta : def;
        return Math.min(cap, Math.max(0, n));
    }
}
