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
    private static void stackableShulkerEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {

        if (!(a.getItem() instanceof BlockItem blockA) || !(b.getItem() instanceof BlockItem blockB)) return;
        if (!(blockA.getBlock() instanceof ShulkerBoxBlock) || !(blockB.getBlock() instanceof ShulkerBoxBlock)) return;

        ContainerComponent containerA = a.get(DataComponentTypes.CONTAINER);
        ContainerComponent containerB = b.get(DataComponentTypes.CONTAINER);

        if (containerA != null && containerB != null && containerA.equals(containerB)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}