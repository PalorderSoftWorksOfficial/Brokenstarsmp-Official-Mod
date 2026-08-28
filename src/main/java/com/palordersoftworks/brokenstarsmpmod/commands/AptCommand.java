package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.modrinth.ModrinthPackageManager;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AptCommand {
    private AptCommand() {
    }

    @FunctionalInterface
    private interface IoCommand {
        int execute(CommandSourceStack source) throws IOException;
    }

    private static int handle(CommandSourceStack source, IoCommand command) {
        try {
            return command.execute(source);
        } catch (IOException e) {
            source.sendFailure(Component.literal("apt: " + e.getMessage()));
            return 0;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        var apt = literal("apt").requires(PermissionUtil::isOwnerOrDev);

        apt.then(literal("search")
                .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> handle(context.getSource(),
                                src -> search(src, StringArgumentType.getString(context, "query"))))));

        apt.then(literal("install")
                .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> handle(context.getSource(),
                                src -> install(src, StringArgumentType.getString(context, "query"))))));

        apt.then(literal("remove")
                .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> handle(context.getSource(),
                                src -> remove(src, StringArgumentType.getString(context, "query"))))));

        apt.then(literal("update")
                .executes(context -> handle(context.getSource(), AptCommand::update)));

        apt.then(literal("list")
                .executes(context -> handle(context.getSource(), AptCommand::list)));

        apt.then(literal("info")
                .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> handle(context.getSource(),
                                src -> info(src, StringArgumentType.getString(context, "query"))))));

        dispatcher.register(apt);
    }

    private static int search(CommandSourceStack source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        if (hits.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No results for: " + query), false);
            return 0;
        }

        String summary = hits.stream()
                .map(hit -> hit.title() + " by " + hit.author() + " [" + hit.slug() + "]")
                .collect(Collectors.joining(" | "));
        source.sendSuccess(() -> Component.literal(summary), false);
        return hits.size();
    }

    private static int install(CommandSourceStack source, String query) throws IOException {
        ModrinthPackageManager.InstallResult result = ModrinthPackageManager.install(query);
        source.sendSuccess(() -> Component.literal("Installed " + result.title() + " " + result.versionNumber() + " as " + result.path().getFileName()), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, String query) throws IOException {
        boolean removed = ModrinthPackageManager.remove(query);
        if (removed) {
            source.sendSuccess(() -> Component.literal("Removed package: " + query), false);
            return 1;
        }
        source.sendFailure(Component.literal("No installed package matched: " + query));
        return 0;
    }

    private static int update(CommandSourceStack source) throws IOException {
        List<ModrinthPackageManager.UpdateResult> updates = ModrinthPackageManager.updateAll();
        if (updates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No package updates available"), false);
            return 0;
        }

        String summary = updates.stream()
                .map(update -> update.title() + " " + update.oldVersion() + " -> " + update.newVersion())
                .collect(Collectors.joining(" | "));
        source.sendSuccess(() -> Component.literal(summary), false);
        return updates.size();
    }

    private static int list(CommandSourceStack source) throws IOException {
        List<ModrinthPackageManager.InstalledPackage> installed = ModrinthPackageManager.listInstalled();
        if (installed.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No apt packages installed"), false);
            return 0;
        }

        String summary = installed.stream()
                .map(pkg -> pkg.title() + " " + pkg.versionNumber())
                .collect(Collectors.joining(" | "));
        source.sendSuccess(() -> Component.literal(summary), false);
        return installed.size();
    }

    private static int info(CommandSourceStack source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        if (hits.isEmpty()) {
            source.sendFailure(Component.literal("No Modrinth project found for: " + query));
            return 0;
        }

        ModrinthPackageManager.SearchHit hit = hits.get(0);
        source.sendSuccess(() -> Component.literal(hit.title() + " | " + hit.slug() + " | " + hit.author() + " | " + hit.description()), false);
        return 1;
    }
}
