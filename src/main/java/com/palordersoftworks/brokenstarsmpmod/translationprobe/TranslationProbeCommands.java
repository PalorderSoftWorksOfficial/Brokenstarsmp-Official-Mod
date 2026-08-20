package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TranslationProbeCommands {
    private TranslationProbeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("bsmpprobe")
                            .requires(src -> src.permissions().hasPermission(
                                    new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                            .then(literal("reload")
                                    .executes(ctx -> {
                                        var server = ctx.getSource().getServer();
                                        TranslationProbeController.reloadConfig(server);
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("[CheckHacks] Reloaded; runtime enabled="
                                                                + TranslationProbeController.isRuntimeEnabled())
                                                        .withStyle(ChatFormatting.GREEN),
                                                true
                                        );
                                        return 1;
                                    }))
                            .then(literal("enable")
                                    .then(argument("value", BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean v = BoolArgumentType.getBool(ctx, "value");
                                                TranslationProbeController.setRuntimeEnabled(v);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("[CheckHacks] Runtime " + (v ? "enabled" : "disabled"))
                                                                .withStyle(v ? ChatFormatting.GREEN : ChatFormatting.RED),
                                                        true
                                                );
                                                return 1;
                                            })))
                            .then(literal("run")
                                    .then(argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                TranslationProbeController.startPlayerCheck(
                                                        target,
                                                        ctx.getSource().getServer(),
                                                        (String) null
                                                );
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("[CheckHacks] Started group check for "
                                                                        + target.getName().getString())
                                                                .withStyle(ChatFormatting.GRAY),
                                                        true
                                                );
                                                return 1;
                                            })
                                            .then(argument("hackId", StringArgumentType.word())
                                                    .executes(ctx -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                        String id = StringArgumentType.getString(ctx, "hackId");
                                                        TranslationProbeController.startPlayerCheck(
                                                                target,
                                                                ctx.getSource().getServer(),
                                                                id
                                                        );
                                                        ctx.getSource().sendSuccess(
                                                                () -> Component.literal("[CheckHacks] Started single hack "
                                                                        + id + " for " + target.getName().getString())
                                                                        .withStyle(ChatFormatting.GRAY),
                                                                true
                                                        );
                                                        return 1;
                                                    }))))
                            .then(literal("status")
                                    .executes(ctx -> {
                                        var cfg = TranslationProbeController.getFileConfig();
                                        int g = cfg.defaultCheckHacks == null ? 0 : cfg.defaultCheckHacks.size();
                                        int j = cfg.autoCheckOnJoin == null || cfg.autoCheckOnJoin.hacks == null
                                                ? 0 : cfg.autoCheckOnJoin.hacks.size();
                                        int h = cfg.hacks == null ? 0 : cfg.hacks.size();
                                        boolean joinOn = cfg.autoCheckOnJoin != null && cfg.autoCheckOnJoin.enabled;
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(String.format(
                                                        "[CheckHacks] runtime=%s file.enabled=%s autoJoin=%s joinHacks=%d default=%d registry=%d timeout=%d between=%d bedrock=%s",
                                                        TranslationProbeController.isRuntimeEnabled(),
                                                        cfg.enabled,
                                                        joinOn,
                                                        j,
                                                        g,
                                                        h,
                                                        cfg.timeoutTicks,
                                                        cfg.betweenSignTicks,
                                                        cfg.bedrock != null && cfg.bedrock.enabled
                                                )).withStyle(ChatFormatting.AQUA),
                                                false
                                        );
                                        return 1;
                                    }))
                            .executes(ctx -> {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Usage: /bsmpprobe reload | enable <bool> | status | run <player> [hackId]")
                                                .withStyle(ChatFormatting.YELLOW),
                                        false
                                );
                                return 0;
                            })
            );
        });
    }
}
