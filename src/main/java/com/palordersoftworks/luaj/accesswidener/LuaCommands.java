package com.palordersoftworks.luaj.accesswidener;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class LuaCommands {
    private static final LuaScriptManager LUA = new LuaScriptManager();

    private static ScriptHost host(CommandSourceStack source) {
        return new ScriptHost(
                msg -> source.sendSystemMessage(Component.literal(msg)),
                msg -> source.sendFailure(Component.literal(msg))
        );
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("lua")
                    .requires(source -> PermissionCompat.gamemaster().test(source))
                    .then(Commands.literal("reload").executes(ctx -> {
                        LUA.reloadAll();
                        return 1;
                    }))
                    .then(Commands.literal("load")
                            .then(Commands.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.load(script, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(Commands.literal("unload")
                            .then(Commands.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.stop(script) ? 1 : 0;
                            })))
                    .then(Commands.literal("run")
                            .then(Commands.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.run(script, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(Commands.literal("stop")
                            .then(Commands.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.stop(script) ? 1 : 0;
                            })))
                    .then(Commands.literal("runCode")
                            .then(Commands.argument("code", StringArgumentType.greedyString()).executes(ctx -> {
                                String code = StringArgumentType.getString(ctx, "code");
                                return LUA.runCode(code, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(Commands.literal("io")
                            .then(Commands.argument("script", StringArgumentType.word())
                                    .then(Commands.argument("input", StringArgumentType.greedyString()).executes(ctx -> {
                                        String script = StringArgumentType.getString(ctx, "script");
                                        String input = StringArgumentType.getString(ctx, "input");
                                        return LUA.pushInput(script, input) ? 1 : 0;
                                    }))))
            );
        });
    }
}