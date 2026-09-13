package com.palordersoftworks.brokenstarsmpmod.teams;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.palordersoftworks.brokenstarsmpmod.config.TeamRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TeamsCommand {
    private TeamsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("teams")
                .requires(source -> TeamRules.TEAMS_ENABLED)
                .executes(TeamsCommand::showHelp)
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(literal("invite").then(argument("player", EntityArgument.player())
                        .executes(ctx -> invite(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("join").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestTeams)
                        .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(literal("leave")
                        .executes(TeamsCommand::leave))
                .then(literal("list")
                        .executes(TeamsCommand::listTeams))
                .then(literal("info").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestTeams)
                        .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(literal("color").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestOwnedTeams)
                        .then(argument("color", StringArgumentType.word())
                                .suggests(TeamsCommand::suggestColors)
                                .executes(ctx -> color(
                                        ctx,
                                        StringArgumentType.getString(ctx, "team"),
                                        StringArgumentType.getString(ctx, "color"))))))
                .then(literal("kick").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestOwnedTeams)
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> kick(
                                        ctx,
                                        StringArgumentType.getString(ctx, "team"),
                                        EntityArgument.getPlayer(ctx, "player"))))))
                .then(literal("transfer").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestOwnedTeams)
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> transfer(
                                        ctx,
                                        StringArgumentType.getString(ctx, "team"),
                                        EntityArgument.getPlayer(ctx, "player"))))))
                .then(literal("disband").then(argument("team", StringArgumentType.word())
                        .suggests(TeamsCommand::suggestOwnedTeams)
                        .executes(ctx -> disband(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(literal("invites")
                        .executes(TeamsCommand::showInvites)));
    }

    private static CompletableFuture<Suggestions> suggestTeams(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                TeamManager.allTeams().stream().map(TeamManager.TeamData::name).collect(Collectors.toList()),
                builder);
    }

    private static CompletableFuture<Suggestions> suggestOwnedTeams(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        UUID actor = context.getSource().getPlayer() != null ? context.getSource().getPlayer().getUUID() : null;
        return SharedSuggestionProvider.suggest(
                TeamManager.allTeams().stream()
                        .filter(team -> actor == null || team.owner().equals(actor))
                        .map(TeamManager.TeamData::name)
                        .collect(Collectors.toList()),
                builder);
    }

    private static CompletableFuture<Suggestions> suggestColors(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "reset"),
                builder);
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("---- Teams ----").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/teams create <name> - found a new team").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams invite <player> - invite a player to your team").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams join <team> - join a team you were invited to").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams leave - leave your current team").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams list - list every team").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams info <team> - team details").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams color <team> <color> - nametag color (owner)").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams kick <team> <player> - remove a member (owner)").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams transfer <team> <player> - hand over ownership (owner)").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams disband <team> - delete your team (owner)").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/teams invites - list your open invites").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.createTeam(name, player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Team " + name + " created! You are the owner.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
        if (team == null) {
            context.getSource().sendFailure(Component.literal("You don't own a team.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target.getUUID().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("You are already the owner.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String error = TeamManager.invite(team.name(), target.getUUID(), player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Invited " + target.getName().getString()
                + " to " + team.name() + ".").withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.literal("You have been invited to join " + team.name()
                + "! Use /teams join " + team.name()).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.join(name, player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("You joined " + name + "!")
                .withStyle(ChatFormatting.GREEN), false);
        TeamManager.TeamData team = TeamManager.getTeam(name);
        if (team != null) {
            for (UUID member : team.members()) {
                if (member.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(member);
                if (online != null) {
                    online.sendSystemMessage(Component.literal(player.getName().getString()
                            + " joined your team!").withStyle(ChatFormatting.AQUA));
                }
            }
        }
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
        String error = TeamManager.leave(player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("You left " + team.name() + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listTeams(CommandContext<CommandSourceStack> context) {
        List<TeamManager.TeamData> teams = TeamManager.allTeams();
        if (teams.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No teams exist yet. Create one with /teams create <name>!")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("---- Teams (" + teams.size() + ") ----")
                .withStyle(ChatFormatting.GOLD), false);
        for (TeamManager.TeamData team : teams) {
            context.getSource().sendSuccess(() -> Component.literal(team.name() + " - " + team.members().size()
                    + " member(s), owner: " + ownerName(context, team.owner()))
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static String ownerName(CommandContext<CommandSourceStack> context, UUID owner) {
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getName().getString();
        }
        return context.getSource().getServer().services().nameToIdCache().get(owner)
                .map(entry -> entry.name())
                .orElse("unknown");
    }

    private static int info(CommandContext<CommandSourceStack> context, String name) {
        TeamManager.TeamData team = TeamManager.getTeam(name);
        if (team == null) {
            context.getSource().sendFailure(Component.literal("No team named " + name + " exists.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String owner = ownerName(context, team.owner());
        String members = team.members().stream()
                .map(uuid -> {
                    ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                    if (online != null) {
                        return online.getName().getString();
                    }
                    return context.getSource().getServer().services().nameToIdCache().get(uuid)
                            .map(entry -> entry.name())
                            .orElse("unknown");
                })
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.literal("---- " + team.name() + " ----")
                .withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Owner: " + owner).withStyle(ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Members (" + team.members().size() + "): " + members)
                .withStyle(ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Color: " + team.color()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> context, String name, String colorInput) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.setColor(name, colorInput, player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Team color updated to " + colorInput.toLowerCase(Locale.ROOT) + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> context, String name, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.kick(name, target.getUUID(), player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Kicked " + target.getName().getString()
                + " from " + name + ".").withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.literal("You were kicked from " + name + ".").withStyle(ChatFormatting.RED));
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> context, String name, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.transfer(name, target.getUUID(), player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Transferred ownership of " + name
                + " to " + target.getName().getString() + ".").withStyle(ChatFormatting.GREEN), false);
        target.sendSystemMessage(Component.literal("You are now the owner of " + name + "!")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String error = TeamManager.deleteTeam(name, player.getUUID());
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Team " + name + " disbanded.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showInvites(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var invites = TeamManager.invitesFor(player.getUUID());
        if (invites.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no open invites.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("Open invites: " + String.join(", ", invites))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
