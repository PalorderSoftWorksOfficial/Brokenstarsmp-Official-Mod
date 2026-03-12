package com.palordersoftworks.brokenstarsmpmod.mixins.client;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Slot.class)
public class SlotStackRenderingMixin {

    // Redirect Slot.getStack() to return the "full stacked" version for client rendering
    @Redirect(method = "getStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;getStack(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack renderStackedShulker(net.minecraft.inventory.Inventory inv, int index) {
        ItemStack stack = inv.getStack(index);

        if (stack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && container.stream().findAny().isPresent()) {
                ItemStack copy = stack.copy();
                copy.setCount(stack.getMaxCount()); // Client sees full stack
                return copy;
            }
        }

        return stack;
    }
}