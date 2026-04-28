package com.palordersoftworks.luaj.accesswidener;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class LuaCommands {
    private static final LuaScriptManager LUA = new LuaScriptManager();

    private static ScriptHost host(ServerCommandSource source) {
        return new ScriptHost(
                msg -> source.sendMessage(Text.literal(msg)),
                msg -> source.sendError(Text.literal(msg))
        );
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("lua")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("reload").executes(ctx -> {
                        LUA.reloadAll();
                        return 1;
                    }))
                    .then(CommandManager.literal("load")
                            .then(CommandManager.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.load(script, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(CommandManager.literal("unload")
                            .then(CommandManager.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.stop(script) ? 1 : 0;
                            })))
                    .then(CommandManager.literal("run")
                            .then(CommandManager.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.run(script, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(CommandManager.literal("stop")
                            .then(CommandManager.argument("script", StringArgumentType.word()).executes(ctx -> {
                                String script = StringArgumentType.getString(ctx, "script");
                                return LUA.stop(script) ? 1 : 0;
                            })))
                    .then(CommandManager.literal("runCode")
                            .then(CommandManager.argument("code", StringArgumentType.greedyString()).executes(ctx -> {
                                String code = StringArgumentType.getString(ctx, "code");
                                return LUA.runCode(code, host(ctx.getSource())) ? 1 : 0;
                            })))
                    .then(CommandManager.literal("io")
                            .then(CommandManager.argument("script", StringArgumentType.word())
                                    .then(CommandManager.argument("input", StringArgumentType.greedyString()).executes(ctx -> {
                                        String script = StringArgumentType.getString(ctx, "script");
                                        String input = StringArgumentType.getString(ctx, "input");
                                        return LUA.pushInput(script, input) ? 1 : 0;
                                    }))))
            );
        });
    }
}