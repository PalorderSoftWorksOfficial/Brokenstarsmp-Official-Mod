package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TranslationProbeCommands {
    private TranslationProbeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("bsmpprobe")
                            .requires(src -> src.getPermissions().hasPermission(
                                    new Permission.Level(PermissionLevel.GAMEMASTERS)))
                            .then(literal("reload")
                                    .executes(ctx -> {
                                        var server = ctx.getSource().getServer();
                                        TranslationProbeController.reloadConfig(server);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("[CheckHacks] Reloaded; runtime enabled="
                                                                + TranslationProbeController.isRuntimeEnabled())
                                                        .formatted(Formatting.GREEN),
                                                true
                                        );
                                        return 1;
                                    }))
                            .then(literal("enable")
                                    .then(argument("value", BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                                TranslationProbeController.setRuntimeEnabled(v);
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("[CheckHacks] Runtime " + (v ? "enabled" : "disabled"))
                                                                .formatted(v ? Formatting.GREEN : Formatting.RED),
                                                        true
                                                );
                                                return 1;
                                            })))
                            .then(literal("run")
                                    .then(argument("player", EntityArgumentType.player())
                                            .executes(ctx -> {
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                TranslationProbeController.startPlayerCheck(
                                                        target,
                                                        ctx.getSource().getServer(),
                                                        null
                                                );
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("[CheckHacks] Started group check for "
                                                                        + target.getName().getString())
                                                                .formatted(Formatting.GRAY),
                                                        true
                                                );
                                                return 1;
                                            })
                                            .then(argument("hackId", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                        String id = StringArgumentType.getString(ctx, "hackId");
                                                        TranslationProbeController.startPlayerCheck(
                                                                target,
                                                                ctx.getSource().getServer(),
                                                                id
                                                        );
                                                        ctx.getSource().sendFeedback(
                                                                () -> Text.literal("[CheckHacks] Started single hack "
                                                                        + id + " for " + target.getName().getString())
                                                                        .formatted(Formatting.GRAY),
                                                                true
                                                        );
                                                        return 1;
                                                    }))))
                            .then(literal("status")
                                    .executes(ctx -> {
                                        var cfg = TranslationProbeController.getFileConfig();
                                        int g = cfg.defaultCheckHacks == null ? 0 : cfg.defaultCheckHacks.size();
                                        int h = cfg.hacks == null ? 0 : cfg.hacks.size();
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal(String.format(
                                                        "[CheckHacks] runtime=%s file.enabled=%s autoJoin=%s detectFlag=%s group=%d registry=%d timeout=%d between=%d",
                                                        TranslationProbeController.isRuntimeEnabled(),
                                                        cfg.enabled,
                                                        cfg.autoCheckOnJoin,
                                                        cfg.detectFlag,
                                                        g,
                                                        h,
                                                        cfg.timeoutTicks,
                                                        cfg.betweenSignTicks
                                                )).formatted(Formatting.AQUA),
                                                false
                                        );
                                        return 1;
                                    }))
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal("Usage: /bsmpprobe reload | enable <bool> | status | run <player> [hackId]")
                                                .formatted(Formatting.YELLOW),
                                        false
                                );
                                return 0;
                            })
            );
        });
    }
}
