package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ImmortalCommand {
    private ImmortalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(literal("immortal")
                .requires(source -> UnstableSMPRules.IMMORTAL_SYSTEM_ENABLED && PermissionUtil.hasImmortalPermission(source))
                .then(argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            boolean enabled = !UnstableSMPFeatures.isImmortal(target);
                            UnstableSMPFeatures.setImmortal(target, enabled);

                            context.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " immortal set to " + enabled).withStyle(ChatFormatting.YELLOW), false);
                            target.connection.send(new ClientboundSystemChatPacket(Component.literal(enabled ? "Immortality enabled" : "Immortality disabled").withStyle(ChatFormatting.AQUA), false));
                            return 1;
                        })));
    }
}
