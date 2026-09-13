package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /rankshop} and {@code /ranktokens} commands.
 *
 * Admin subcommands accept online players or offline names (resolved through
 * EconomyCraft's own profile resolver), and are gated behind op level 2 with an
 * optional permission-node override ({@code brokenstarsmp.ranktokens.admin}).
 */
public final class RankShopCommands {
    private RankShopCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rankshop")
                .requires(source -> RankShopConfig.RANK_SHOP_ENABLED && source.getPlayer() != null)
                .executes(context -> {
                    RankShopUi.openShop(context.getSource().getPlayerOrException());
                    return 1;
                }));

        var rankTokens = Commands.literal("ranktokens")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    sendBalance(context.getSource(), player.getUUID());
                    return 1;
                });

        // /ranktokens balance [player]
        rankTokens.then(Commands.literal("balance")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    sendBalance(context.getSource(), player.getUUID());
                    return 1;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> isAdmin(source))
                        .executes(context -> {
                            UUID target = resolveTarget(context, "player");
                            if (target == null) {
                                context.getSource().sendFailure(
                                        Component.literal("Unknown player.").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            sendBalance(context.getSource(), target);
                            return 1;
                        })));

        // /ranktokens give <player> <amount>
        rankTokens.then(Commands.literal("give")
                .requires(RankShopCommands::isAdmin)
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> {
                                    long amount = LongArgumentType.getLong(context, "amount");
                                    UUID target = resolveTarget(context, "player");
                                    if (target == null) {
                                        context.getSource().sendFailure(
                                                Component.literal("Unknown player.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    RankShopService.giveTokens(target, amount);
                                    feedback(context, "Gave " + amount + " Rank Token(s).");
                                    return 1;
                                }))));

        // /ranktokens take <player> <amount>
        rankTokens.then(Commands.literal("take")
                .requires(RankShopCommands::isAdmin)
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> {
                                    long amount = LongArgumentType.getLong(context, "amount");
                                    UUID target = resolveTarget(context, "player");
                                    if (target == null) {
                                        context.getSource().sendFailure(
                                                Component.literal("Unknown player.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    if (!RankShopService.takeTokens(target, amount)) {
                                        context.getSource().sendFailure(
                                                Component.literal("Player does not have that many Rank Tokens.")
                                                        .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    feedback(context, "Took " + amount + " Rank Token(s).");
                                    return 1;
                                }))));

        // /ranktokens set <player> <amount>
        rankTokens.then(Commands.literal("set")
                .requires(RankShopCommands::isAdmin)
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> {
                                    long amount = LongArgumentType.getLong(context, "amount");
                                    UUID target = resolveTarget(context, "player");
                                    if (target == null) {
                                        context.getSource().sendFailure(
                                                Component.literal("Unknown player.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    if (!RankShopService.setTokens(target, amount)) {
                                        context.getSource().sendFailure(
                                                Component.literal("Could not set the balance.")
                                                        .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    feedback(context, "Set balance to " + amount + " Rank Token(s).");
                                    return 1;
                                }))));

        dispatcher.register(rankTokens);
    }

    private static boolean isAdmin(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return true; // console
        }
        if (source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) {
            return true;
        }
        // Optional permission-node override via fabric-permissions-api, when present.
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Object result = permissionsClass.getMethod("check", ServerPlayer.class, String.class, boolean.class)
                    .invoke(null, source.getPlayer(), RankShopConfig.RANK_SHOP_ADMIN_PERMISSION, false);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** Resolves an online or offline player name via EconomyCraft's resolver. */
    private static UUID resolveTarget(CommandContext<CommandSourceStack> context, String arg) {
        String name = StringArgumentType.getString(context, arg);
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        return com.reazip.economycraft.EconomyCraft
                .getManager(context.getSource().getServer())
                .tryResolveUuidByName(name);
    }

    private static void sendBalance(CommandSourceStack source, UUID target) {
        long tokens = RankShopService.store().getBalance(target);
        Component message = RankShopService.render(RankShopConfig.RANK_SHOP_MSG_BALANCE, Map.of(
                "tokens", String.valueOf(tokens)));
        source.sendSuccess(() -> message, false);
    }

    private static void feedback(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }
}
