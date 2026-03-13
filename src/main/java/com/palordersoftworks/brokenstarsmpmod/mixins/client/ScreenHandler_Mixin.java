package com.palordersoftworks.brokenstarsmpmod.mixins.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScreenHandler.class)
public class ScreenHandler_Mixin {

    @ModifyReturnValue(method = "getCursorStack", at = @At("RETURN"))
    private ItemStack modifyCursorStack(ItemStack original) {
        if (original.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = original.get(DataComponentTypes.CONTAINER);
            if (container != null && container.stream().findAny().isPresent()) {
                ItemStack copy = original.copy();
                copy.setCount(copy.getMaxCount());
                return copy;
            }
        }
        return original;
    }

    @ModifyReturnValue(method = "getSlot", at = @At("RETURN"))
    private Slot modifySlot(Slot slot) {
        ItemStack stack = slot.getStack();

        if (stack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && container.stream().findAny().isPresent()) {
                ItemStack copy = stack.copy();
                copy.setCount(copy.getMaxCount());
                slot.setStackNoCallbacks(copy);
            }
        }

        return slot;
    }
}