package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.modrinth.ModrinthPackageManager;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AptCommand {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BrokenStarSMP-apt");
        thread.setDaemon(true);
        return thread;
    });

    private AptCommand() {
    }

    @FunctionalInterface
    private interface IoCommand {
        void execute(CommandSourceStack source) throws IOException;
    }

    private static int handle(CommandSourceStack source, IoCommand command) {
        IO_EXECUTOR.execute(() -> {
            try {
                command.execute(source);
            } catch (IOException e) {
                source.getServer().execute(() -> source.sendFailure(Component.literal("apt: " + e.getMessage())));
            } catch (RuntimeException e) {
                source.getServer().execute(() -> source.sendFailure(Component.literal("apt: " + e.getMessage())));
            }
        });
        source.sendSuccess(() -> Component.literal("apt: operation started"), false);
        return 1;
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

    private static void search(CommandSourceStack source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        source.getServer().execute(() -> {
            if (hits.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No results for: " + query), false);
                return;
            }

            String summary = hits.stream()
                    .map(hit -> hit.title() + " by " + hit.author() + " [" + hit.slug() + "]")
                    .collect(Collectors.joining(" | "));
            source.sendSuccess(() -> Component.literal(summary), false);
        });
    }

    private static void install(CommandSourceStack source, String query) throws IOException {
        ModrinthPackageManager.InstallResult result = ModrinthPackageManager.install(query);
        source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
                "Installed " + result.title() + " " + result.versionNumber() + " as " + result.path().getFileName()), false));
    }

    private static void remove(CommandSourceStack source, String query) throws IOException {
        boolean removed = ModrinthPackageManager.remove(query);
        source.getServer().execute(() -> {
            if (removed) {
                source.sendSuccess(() -> Component.literal("Removed package: " + query), false);
            } else {
                source.sendFailure(Component.literal("No installed package matched: " + query));
            }
        });
    }

    private static void update(CommandSourceStack source) throws IOException {
        List<ModrinthPackageManager.UpdateResult> updates = ModrinthPackageManager.updateAll();
        source.getServer().execute(() -> {
            if (updates.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No package updates available"), false);
                return;
            }

            String summary = updates.stream()
                    .map(update -> update.title() + " " + update.oldVersion() + " -> " + update.newVersion())
                    .collect(Collectors.joining(" | "));
            source.sendSuccess(() -> Component.literal(summary), false);
        });
    }

    private static void list(CommandSourceStack source) throws IOException {
        List<ModrinthPackageManager.InstalledPackage> installed = ModrinthPackageManager.listInstalled();
        source.getServer().execute(() -> {
            if (installed.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No apt packages installed"), false);
                return;
            }

            String summary = installed.stream()
                    .map(pkg -> pkg.title() + " " + pkg.versionNumber())
                    .collect(Collectors.joining(" | "));
            source.sendSuccess(() -> Component.literal(summary), false);
        });
    }

    private static void info(CommandSourceStack source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        source.getServer().execute(() -> {
            if (hits.isEmpty()) {
                source.sendFailure(Component.literal("No Modrinth project found for: " + query));
                return;
            }

            ModrinthPackageManager.SearchHit hit = hits.get(0);
            source.sendSuccess(() -> Component.literal(
                    hit.title() + " | " + hit.slug() + " | " + hit.author() + " | " + hit.description()), false);
        });
    }
}
