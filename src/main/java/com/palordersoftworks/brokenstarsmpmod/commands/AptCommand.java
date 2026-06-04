package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.modrinth.ModrinthPackageManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AptCommand {
    private AptCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("apt")
                .requires(PermissionUtil::isOwnerOrDev)
                .then(literal("search")
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    try {
                                        return search(context.getSource(), StringArgumentType.getString(context, "query"));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })))
                .then(literal("install")
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    try {
                                        return install(context.getSource(), StringArgumentType.getString(context, "query"));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })))
                .then(literal("remove")
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    try {
                                        return remove(context.getSource(), StringArgumentType.getString(context, "query"));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })))
                .then(literal("update")
                        .executes(context -> {
                            try {
                                return update(context.getSource());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .then(literal("list")
                        .executes(context -> {
                            try {
                                return list(context.getSource());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .then(literal("info")
                        .then(argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    try {
                                        return info(context.getSource(), StringArgumentType.getString(context, "query"));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }))));
    }

    private static int search(ServerCommandSource source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        if (hits.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No results for: " + query), false);
            return 0;
        }

        String summary = hits.stream()
                .map(hit -> hit.title() + " by " + hit.author() + " [" + hit.slug() + "]")
                .collect(Collectors.joining(" | "));
        source.sendFeedback(() -> Text.literal(summary), false);
        return hits.size();
    }

    private static int install(ServerCommandSource source, String query) throws IOException {
        ModrinthPackageManager.InstallResult result = ModrinthPackageManager.install(query);
        source.sendFeedback(() -> Text.literal("Installed " + result.title() + " " + result.versionNumber() + " as " + result.path().getFileName()), false);
        return 1;
    }

    private static int remove(ServerCommandSource source, String query) throws IOException {
        boolean removed = ModrinthPackageManager.remove(query);
        if (removed) {
            source.sendFeedback(() -> Text.literal("Removed package: " + query), false);
            return 1;
        }
        source.sendError(Text.literal("No installed package matched: " + query));
        return 0;
    }

    private static int update(ServerCommandSource source) throws IOException {
        List<ModrinthPackageManager.UpdateResult> updates = ModrinthPackageManager.updateAll();
        if (updates.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No package updates available"), false);
            return 0;
        }

        String summary = updates.stream()
                .map(update -> update.title() + " " + update.oldVersion() + " -> " + update.newVersion())
                .collect(Collectors.joining(" | "));
        source.sendFeedback(() -> Text.literal(summary), false);
        return updates.size();
    }

    private static int list(ServerCommandSource source) throws IOException {
        List<ModrinthPackageManager.InstalledPackage> installed = ModrinthPackageManager.listInstalled();
        if (installed.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No apt packages installed"), false);
            return 0;
        }

        String summary = installed.stream()
                .map(pkg -> pkg.title() + " " + pkg.versionNumber())
                .collect(Collectors.joining(" | "));
        source.sendFeedback(() -> Text.literal(summary), false);
        return installed.size();
    }

    private static int info(ServerCommandSource source, String query) throws IOException {
        List<ModrinthPackageManager.SearchHit> hits = ModrinthPackageManager.search(query);
        if (hits.isEmpty()) {
            source.sendError(Text.literal("No Modrinth project found for: " + query));
            return 0;
        }

        ModrinthPackageManager.SearchHit hit = hits.get(0);
        source.sendFeedback(() -> Text.literal(hit.title() + " | " + hit.slug() + " | " + hit.author() + " | " + hit.description()), false);
        return 1;
    }
}