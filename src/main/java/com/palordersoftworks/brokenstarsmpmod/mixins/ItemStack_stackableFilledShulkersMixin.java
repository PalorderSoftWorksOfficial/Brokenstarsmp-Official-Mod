package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStack_stackableFilledShulkersMixin {

    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void stackFilledShulkers(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);

            if (container != null && container.stream().findAny().isPresent()) {
                cir.setReturnValue(64);
            }
        }
    }
    @Inject(method = "areItemsAndComponentsEqual", at = @At("HEAD"), cancellable = true)
    private static void areItemsAndComponentsEqual(ItemStack stack, ItemStack other, CallbackInfoReturnable<Boolean> cir) {

        if (!(stack.getItem() instanceof BlockItem blockA) || !(other.getItem() instanceof BlockItem blockB)) return;
        if (!(blockA.getBlock() instanceof ShulkerBoxBlock) || !(blockB.getBlock() instanceof ShulkerBoxBlock)) return;

        ContainerComponent containerA = stack.get(DataComponentTypes.CONTAINER);
        ContainerComponent containerB = other.get(DataComponentTypes.CONTAINER);

        if (containerA != null && containerB != null &&
                containerA.stream().findAny().isPresent() &&
                containerB.stream().findAny().isPresent()) {

            if (containerA.equals(containerB)) {
                cir.setReturnValue(true);
            }
        }
    }
}