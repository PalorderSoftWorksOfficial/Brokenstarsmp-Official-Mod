package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(targets = "com.fibermc.essentialcommands.commands.bench.EnderchestCommand")
public class EnderchestCommand_Mixin {

    @Unique
    private static final Map<UUID, ItemStack[]> SNAPSHOTS = new HashMap<>();

    @Unique
    private static ItemStack[] snapshotOf(Inventory inv) {
        ItemStack[] snapshot = new ItemStack[inv.size()];
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            snapshot[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        return snapshot;
    }

    @Unique
    private static SimpleInventory loadSnapshot(int size, ItemStack[] stored) {
        SimpleInventory inv = new SimpleInventory(size);

        for (int i = 0; i < size; i++) {
            if (stored != null && i < stored.length && stored[i] != null && !stored[i].isEmpty()) {
                inv.setStack(i, stored[i].copy());
            }
        }

        return inv;
    }

    @Unique
    private static void saveSnapshot(UUID uuid, Inventory inv) {
        SNAPSHOTS.put(uuid, snapshotOf(inv));
    }

    @Inject(method = "getScreenHandlerFactory*", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmp$replaceFactory(CallbackInfoReturnable<NamedScreenHandlerFactory> cir) {
        cir.setReturnValue(new SimpleNamedScreenHandlerFactory((syncId, inventory, player) -> {
            EnderChestInventory echestInventory = player.getEnderChestInventory();
            UUID uuid = player.getUuid();
            ItemStack[] stored = SNAPSHOTS.get(uuid);

            if (echestInventory.size() == 27) {
                SimpleInventory snapshot = loadSnapshot(27, stored);
                return new GenericContainerScreenHandler(
                        GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, snapshot).getType(),
                        syncId,
                        inventory,
                        snapshot,
                        3
                ) {
                    @Override
                    public void onClosed(PlayerEntity playerEntity) {
                        super.onClosed(playerEntity);
                        if (playerEntity instanceof ServerPlayerEntity serverPlayer) {
                            saveSnapshot(serverPlayer.getUuid(), snapshot);
                        }
                    }
                };
            } else if (echestInventory.size() == 54) {
                SimpleInventory snapshot = loadSnapshot(54, stored);
                return new GenericContainerScreenHandler(
                        GenericContainerScreenHandler.createGeneric9x6(syncId, inventory, snapshot).getType(),
                        syncId,
                        inventory,
                        snapshot,
                        6
                ) {
                    @Override
                    public void onClosed(PlayerEntity playerEntity) {
                        super.onClosed(playerEntity);
                        if (playerEntity instanceof ServerPlayerEntity serverPlayer) {
                            saveSnapshot(serverPlayer.getUuid(), snapshot);
                        }
                    }
                };
            } else {
                return GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, echestInventory);
            }
        }, Text.translatable("container.enderchest")));
    }
}