package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ImmortalCommand {
    private ImmortalCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("immortal")
                .requires(source -> UnstableSMPRules.IMMORTAL_SYSTEM_ENABLED && PermissionUtil.hasImmortalPermission(source))
                .then(argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            boolean enabled = !UnstableSMPFeatures.isImmortal(target);
                            UnstableSMPFeatures.setImmortal(target, enabled);

                            context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " immortal set to " + enabled).formatted(Formatting.YELLOW), false);
                            target.networkHandler.sendPacket(new GameMessageS2CPacket(Text.literal(enabled ? "Immortality enabled" : "Immortality disabled").formatted(Formatting.AQUA), false));
                            return 1;
                        })));
    }
}
