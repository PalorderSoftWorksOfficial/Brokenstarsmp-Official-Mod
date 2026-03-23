package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemDispenserBehavior.class)
public abstract class ItemDispenserBehaviorMixin {

    @Redirect(
            method = "dispenseSilently",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack brokenstarsmpmod$fullStack(ItemStack stack, int amount) {
        return stack.split(stack.getCount());
    }
}