package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class LinkFishingRod {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("linkfrod")
                .requires(PermissionUtil::isOwnerOrDev)
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    ItemStack stack = player.getMainHandStack();

                    if (!(stack.getItem() instanceof FishingRodItem)) return 0;

                    NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
                        nbt.putString("RodType", "void");
                        nbt.putString("Voidrodowner", player.getUuidAsString());
                        nbt.putInt("RodUse", 0);
                    });

                    stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Stasis rod"));
                    return 1;
                }));
    }
}