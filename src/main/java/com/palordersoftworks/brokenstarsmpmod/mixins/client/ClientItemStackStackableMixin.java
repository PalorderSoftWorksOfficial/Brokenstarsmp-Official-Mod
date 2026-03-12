package com.palordersoftworks.brokenstarsmpmod.mixins.client;

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
public class ClientItemStackStackableMixin {
    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void clientMaxCount(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && container.stream().findAny().isPresent()) {
                cir.setReturnValue(64);
            }
        }
    }
}