package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(targets = "com.fibermc.essentialcommands.commands.bench.EnderchestCommand")
public class EnderchestCommand_Mixin {

    private static final Map<UUID, NbtList> SNAPSHOTS = new HashMap<>();

    private static SimpleInventory loadSnapshot(ServerPlayerEntity player, int size) {
        SimpleInventory inv = new SimpleInventory(size);
        NbtList list = SNAPSHOTS.get(player.getUuid());
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);
                int slot = tag.getInt("Slot");
                if (slot >= 0 && slot < size) {
                    inv.setStack(slot, ItemStack.fromNbt(tag));
                }
            }
        }
        return inv;
    }

    private static void saveSnapshot(ServerPlayerEntity player, SimpleInventory inv) {
        NbtList list = new NbtList();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound tag = new NbtCompound();
                tag.putInt("Slot", i);
                stack.writeNbt(tag);
                list.add(tag);
            }
        }
        SNAPSHOTS.put(player.getUuid(), list);
    }

    @Redirect(
            method = "getScreenHandlerFactory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getEnderChestInventory()Lnet/minecraft/inventory/EnderChestInventory;"
            )
    )
    private SimpleInventory useSnapshot(ServerPlayerEntity player) {
        int size = player.getEnderChestInventory().size();
        return loadSnapshot(player, size);
    }

    @Redirect(
            method = "getScreenHandlerFactory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/screen/GenericContainerScreenHandler;createGeneric9x3(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/inventory/Inventory;)Lnet/minecraft/screen/GenericContainerScreenHandler;"
            )
    )
    private GenericContainerScreenHandler wrap3(int syncId, PlayerInventory inv, Inventory container) {
        GenericContainerScreenHandler original = GenericContainerScreenHandler.createGeneric9x3(syncId, inv, container);
        return new GenericContainerScreenHandler(original.getType(), syncId, inv, container, 3) {
            @Override
            public void onClosed(net.minecraft.entity.player.PlayerEntity player) {
                super.onClosed(player);
                saveSnapshot((ServerPlayerEntity) player, (SimpleInventory) container);
            }
        };
    }

    @Redirect(
            method = "getScreenHandlerFactory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/screen/GenericContainerScreenHandler;createGeneric9x6(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/inventory/Inventory;)Lnet/minecraft/screen/GenericContainerScreenHandler;"
            )
    )
    private GenericContainerScreenHandler wrap6(int syncId, PlayerInventory inv, Inventory container) {
        GenericContainerScreenHandler original = GenericContainerScreenHandler.createGeneric9x6(syncId, inv, container);
        return new GenericContainerScreenHandler(original.getType(), syncId, inv, container, 6) {
            @Override
            public void onClosed(net.minecraft.entity.player.PlayerEntity player) {
                super.onClosed(player);
                saveSnapshot((ServerPlayerEntity) player, (SimpleInventory) container);
            }
        };
    }
}