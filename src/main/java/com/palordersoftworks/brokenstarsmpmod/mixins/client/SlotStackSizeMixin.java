package com.palordersoftworks.brokenstarsmpmod.mixins.client;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotStackSizeMixin {

    @Inject(method = "getMaxItemCount(Lnet/minecraft/item/ItemStack;)I",
            at = @At("HEAD"),
            cancellable = true)
    private void allowStackedShulkers(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);

            if (container != null && container.stream().findAny().isPresent()) {
                cir.setReturnValue(64);
            }
        }
    }

    @Inject(method = "getMaxItemCount()I",
            at = @At("HEAD"),
            cancellable = true)
    private void allowStackedShulkersNoArg(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(64);
    }
}