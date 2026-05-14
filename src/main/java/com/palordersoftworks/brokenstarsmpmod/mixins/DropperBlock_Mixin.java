package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.DropperBlock;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DropperBlock.class)
public abstract class DropperBlock_Mixin {

    @Unique
    private int brokenstarsmpmod$getSafeAmount(ItemStack stack) {
        int ruleAmount = Math.max(1, ServerRules.DISPENSER_DROP_AMOUNT);
        if (stack.isEmpty()) {
            return 0;
        }
        return Math.min(ruleAmount, stack.getCount());
    }

    @Redirect(
            method = "dispense",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;copyWithCount(I)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack brokenstarsmpmod$copyWithCustomCount(ItemStack stack, int amount) {
        return stack.copyWithCount(brokenstarsmpmod$getSafeAmount(stack));
    }

    @Redirect(
            method = "dispense",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrement(I)V"
            )
    )
    private void brokenstarsmpmod$decrementByCustomCount(ItemStack stack, int amount) {
        stack.decrement(brokenstarsmpmod$getSafeAmount(stack));
    }
}