package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class LinkFishingRod {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("linkfrod")
                .requires(source -> {
                    ServerPlayer player = source.getPlayer();
                    if (player == null) return false;

                    NameAndId entry = player.nameAndId();

                    return player.level()
                            .getServer()
                            .getPlayerList()
                            .isOp(entry);
                })
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();

                    if (!(stack.getItem() instanceof FishingRodItem)) return 0;

                    CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
                        nbt.putString("RodType", "void");
                        nbt.putString("Voidrodowner", player.getStringUUID());
                        nbt.putInt("RodUse", 0);
                    });

                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Stasis rod"));
                    return 1;
                }));
    }
}