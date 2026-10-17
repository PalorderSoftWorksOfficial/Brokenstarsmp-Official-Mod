package com.palordersoftworks.brokenstarsmpmod.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.palordersoftworks.brokenstarsmpmod.api.BrokenStarsApi;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.palordersoftworks.brokenstarsmpmod.permissions.PermissionNodes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public final class PlayerAdminCommands {
    private PlayerAdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        var root = Commands.literal("bsmplayer")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.ADMINISTRATOR,
                        PermissionNodes.MODERATOR, PermissionNodes.GAMEMASTER));

        var inspect = Commands.literal("inspect");
        inspect.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> inspect(context.getSource(), EntityArgument.getPlayer(context, "player"))));
        root.then(inspect);

        var heal = Commands.literal("heal");
        var healPlayer = Commands.argument("player", EntityArgument.player());
        healPlayer.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.1))
                .executes(context -> health(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        DoubleArgumentType.getDouble(context, "amount"), true)));
        heal.then(healPlayer);
        root.then(heal);

        var feed = Commands.literal("feed");
        feed.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> feed(context.getSource(), EntityArgument.getPlayer(context, "player"))));
        root.then(feed);

        var effects = Commands.literal("effects");
        var clear = Commands.literal("clear");
        clear.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> clearEffects(context.getSource(), EntityArgument.getPlayer(context, "player"))));
        effects.then(clear);
        root.then(effects);

        var gameMode = Commands.literal("gamemode");
        var modePlayer = Commands.argument("player", EntityArgument.player());
        modePlayer.then(Commands.argument("mode", StringArgumentType.word())
                .executes(context -> gameMode(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        StringArgumentType.getString(context, "mode"))));
        gameMode.then(modePlayer);
        root.then(gameMode);

        var teleport = Commands.literal("teleport");
        var teleportPlayer = Commands.argument("player", EntityArgument.player());
        var x = Commands.argument("x", DoubleArgumentType.doubleArg());
        var y = Commands.argument("y", DoubleArgumentType.doubleArg());
        var z = Commands.argument("z", DoubleArgumentType.doubleArg());
        z.executes(context -> teleport(context.getSource(), EntityArgument.getPlayer(context, "player"),
                DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                DoubleArgumentType.getDouble(context, "z")));
        y.then(z);
        x.then(y);
        teleportPlayer.then(x);
        teleport.then(teleportPlayer);
        root.then(teleport);

        var give = Commands.literal("give");
        var givePlayer = Commands.argument("player", EntityArgument.player());
        var item = Commands.argument("item", ItemArgument.item(buildContext));
        var amount = Commands.argument("amount", IntegerArgumentType.integer(1));
        amount.executes(context -> give(context.getSource(), EntityArgument.getPlayer(context, "player"),
                ItemArgument.getItem(context, "item").createItemStack(Math.min(2304, IntegerArgumentType.getInteger(context, "amount")))));
        item.then(amount);
        givePlayer.then(item);
        give.then(givePlayer);
        root.then(give);

        var money = Commands.literal("money");
        var moneyPlayer = Commands.argument("player", EntityArgument.player());
        var moneyAmount = Commands.argument("amount", LongArgumentType.longArg());
        moneyAmount.executes(context -> money(context.getSource(), EntityArgument.getPlayer(context, "player"),
                LongArgumentType.getLong(context, "amount")));
        moneyPlayer.then(moneyAmount);
        money.then(moneyPlayer);
        root.then(money);

        var tokenPlayer = Commands.argument("player", EntityArgument.player());
        var tokenSet = Commands.literal("set");
        tokenSet.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(PlayerAdminCommands::setTokensCommand));
        var tokenAdd = Commands.literal("add");
        tokenAdd.then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(PlayerAdminCommands::addTokensCommand));
        tokenPlayer.then(tokenSet).then(tokenAdd);
        var tokens = Commands.literal("tokens");
        tokens.then(tokenPlayer);
        root.then(tokens);

        var punish = Commands.literal("punish");
        var punishPlayer = Commands.argument("player", EntityArgument.player());
        var ban = Commands.literal("ban");
        ban.then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> punish(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        PunishmentAction.BAN, StringArgumentType.getString(context, "reason"), 0L)));
        var tempBan = Commands.literal("tempban");
        var seconds = Commands.argument("seconds", LongArgumentType.longArg(1L));
        seconds.then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> punish(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        PunishmentAction.TEMPBAN, StringArgumentType.getString(context, "reason"),
                        LongArgumentType.getLong(context, "seconds"))));
        tempBan.then(seconds);
        var kick = Commands.literal("kick");
        kick.then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> punish(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        PunishmentAction.KICK, StringArgumentType.getString(context, "reason"), 0L)));
        var unban = Commands.literal("unban");
        unban.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> punish(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        PunishmentAction.UNBAN, "", 0L)));
        var mute = Commands.literal("mute");
        var muteSeconds = Commands.argument("seconds", LongArgumentType.longArg(1L));
        muteSeconds.then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> punish(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        PunishmentAction.MUTE, StringArgumentType.getString(context, "reason"),
                        LongArgumentType.getLong(context, "seconds"))));
        mute.then(muteSeconds);
        punish.then(ban).then(tempBan).then(kick).then(unban).then(mute);
        root.then(punish);

        root.then(Commands.literal("punishments").then(Commands.argument("player", EntityArgument.player())
                .executes(context -> listPunishments(context.getSource(), EntityArgument.getPlayer(context, "player")))));

        dispatcher.register(root);
    }

    private enum PunishmentAction {
        BAN, TEMPBAN, KICK, UNBAN, MUTE
    }

    private static int punish(CommandSourceStack source, ServerPlayer target, PunishmentAction action,
                              String reason, long seconds) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        boolean ok;
        String targetName = target.getName().getString();
        ok = switch (action) {
            case BAN -> com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.ban(
                    server, target.getUUID(), targetName, reason, source);
            case TEMPBAN -> com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.tempBan(
                    server, target.getUUID(), targetName, seconds * 20L, reason, source);
            case KICK -> com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.kick(
                    server, target.getUUID(), targetName, reason, source);
            case UNBAN -> com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.unban(
                    server, target.getUUID());
            case MUTE -> com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.mute(
                    server, target.getUUID(), targetName, seconds * 20L, reason, source);
        };
        if (!ok) {
            source.sendFailure(Component.literal("Punishment failed (BanHammer absent for mute?).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        send(source, action.name().toLowerCase(java.util.Locale.ROOT) + " applied to " + targetName);
        audit(source, "player.punish." + action.name().toLowerCase(java.util.Locale.ROOT),
                target.getUUID(), "", reason);
        return 1;
    }

    private static int listPunishments(CommandSourceStack source, ServerPlayer target) {
        java.util.Map<String, String> active =
                com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService.activePunishments(
                        target.getUUID());
        if (active.isEmpty()) {
            send(source, target.getName().getString() + " has no active punishments.");
        } else {
            active.forEach((type, expiry) -> send(source, target.getName().getString() + " " + type + ": " + expiry));
        }
        audit(source, "player.punishments.list", target.getUUID(), "read", active.size());
        return 1;
    }

    private static int setTokensCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return tokens(context.getSource(), EntityArgument.getPlayer(context, "player"),
                LongArgumentType.getLong(context, "amount"), true);
    }

    private static int addTokensCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return tokens(context.getSource(), EntityArgument.getPlayer(context, "player"),
                LongArgumentType.getLong(context, "amount"), false);
    }

    private static int inspect(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal(player.getName().getString()
                + " health=" + player.getHealth()
                + " food=" + player.getFoodData().getFoodLevel()
                + " saturation=" + player.getFoodData().getSaturationLevel()
                + " xp=" + player.experienceLevel
                + " world=" + player.level().dimension().identifier()
                + " x=" + player.getX() + " y=" + player.getY() + " z=" + player.getZ()).withStyle(ChatFormatting.AQUA), false);
        audit(source, "player.inspect", player.getUUID(), "state", "read");
        return 1;
    }

    private static int health(CommandSourceStack source, ServerPlayer player, double amount, boolean heal) {
        float value = (float) Math.min(player.getMaxHealth(), Math.max(0.0, heal ? player.getHealth() + amount : amount));
        player.setHealth(value);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(value,
                player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
        send(source, "Set health for " + player.getName().getString() + " to " + value);
        audit(source, "player.health", player.getUUID(), player.getHealth(), value);
        return 1;
    }

    private static int feed(CommandSourceStack source, ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        send(source, "Fed " + player.getName().getString());
        audit(source, "player.feed", player.getUUID(), "hunger", "full");
        return 1;
    }

    private static int clearEffects(CommandSourceStack source, ServerPlayer player) {
        player.removeAllEffects();
        send(source, "Cleared effects for " + player.getName().getString());
        audit(source, "player.effects.clear", player.getUUID(), "active", "cleared");
        return 1;
    }

    private static int gameMode(CommandSourceStack source, ServerPlayer player, String value) {
        GameType mode;
        try {
            mode = GameType.byName(value);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown gamemode.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.setGameMode(mode);
        send(source, "Set gamemode for " + player.getName().getString() + " to " + mode.getName());
        audit(source, "player.gamemode", player.getUUID(), player.gameMode().getName(), mode.getName());
        return 1;
    }

    private static int teleport(CommandSourceStack source, ServerPlayer player, double x, double y, double z) {
        player.teleportTo(player.level(), x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        send(source, "Teleported " + player.getName().getString());
        audit(source, "player.teleport", player.getUUID(), "location", x + "," + y + "," + z);
        return 1;
    }

    private static int give(CommandSourceStack source, ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        send(source, "Gave " + stack.getCount() + " item(s) to " + player.getName().getString());
        audit(source, "player.give", player.getUUID(), "inventory", stack.getItem());
        return 1;
    }

    private static int money(CommandSourceStack source, ServerPlayer player, long amount) {
        if (amount >= 0) {
            BrokenStarsApi.addMoney(source.getServer(), player.getUUID(), amount);
        } else if (!BrokenStarsApi.removeMoney(source.getServer(), player.getUUID(), -amount)) {
            source.sendFailure(Component.literal("Player does not have enough money.").withStyle(ChatFormatting.RED));
            return 0;
        }
        send(source, "Adjusted money for " + player.getName().getString());
        audit(source, "player.money", player.getUUID(), "balance", amount);
        return 1;
    }

    private static int tokens(CommandSourceStack source, ServerPlayer player, long amount, boolean set) {
        boolean success;
        if (set) {
            success = BrokenStarsApi.setTokens(player.getUUID(), amount);
        } else {
            BrokenStarsApi.giveTokens(player.getUUID(), amount);
            success = true;
        }
        if (!success && set) {
            source.sendFailure(Component.literal("Could not set rank tokens.").withStyle(ChatFormatting.RED));
            return 0;
        }
        send(source, (set ? "Set " : "Added ") + amount + " rank token(s) for " + player.getName().getString());
        audit(source, "player.tokens", player.getUUID(), "tokens", amount);
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    private static void audit(CommandSourceStack source, String action, UUID target, Object previous, Object next) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        if (framework != null) {
            framework.recordAudit(source.getPlayer() == null ? null : source.getPlayer().getUUID(),
                    source.getPlayer() == null ? "console" : source.getPlayer().getName().getString(),
                    action, target.toString(), previous, next, "command");
        }
    }
}
