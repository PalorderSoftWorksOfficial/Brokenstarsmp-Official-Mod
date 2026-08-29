package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ImmortalCommand {
    private ImmortalCommand() {
    }

    public static void register(Commands commands) {
        commands.register(Commands.literal("immortal")
                .requires(source ->
                        UnstableSMPRules.IMMORTAL_SYSTEM_ENABLED && hasImmortalPermission(source))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            Player target = Bukkit.getPlayerExact(playerName);

                            if (target == null) {
                                context.getSource().getSender().sendMessage(
                                        Component.text("Player not found: " + playerName)
                                                .color(NamedTextColor.RED));
                                return 0;
                            }

                            boolean enabled = !UnstableSMPFeatures.isImmortal(target);
                            UnstableSMPFeatures.setImmortal(target, enabled);

                            context.getSource().getSender().sendMessage(
                                    Component.text(target.getName() + " immortal set to " + enabled)
                                            .color(NamedTextColor.YELLOW));
                            target.sendMessage(
                                    Component.text(enabled ? "Immortality enabled" : "Immortality disabled")
                                            .color(NamedTextColor.AQUA));
                            return 1;
                        }))
                .build());
    }

    private static boolean hasImmortalPermission(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        return sender.hasPermission("brokenstarsmp.immortal") || sender.isOp();
    }
}
