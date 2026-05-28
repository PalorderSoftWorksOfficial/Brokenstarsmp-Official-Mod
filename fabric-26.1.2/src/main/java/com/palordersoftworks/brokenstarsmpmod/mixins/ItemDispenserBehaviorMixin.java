package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
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
    private ItemStack brokenstarsmpmod$controlledDropAmount(ItemStack stack, int amount) {

        int ruleAmount = ServerRules.DISPENSER_DROP_AMOUNT;

        if (ruleAmount <= 0) {
            return stack.split(1);
        }

        int toDrop = Math.min(ruleAmount, stack.getCount());

        return stack.split(toDrop);
    }
}