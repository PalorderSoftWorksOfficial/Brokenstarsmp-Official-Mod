package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DefaultDispenseItemBehavior.class)
public abstract class ItemDispenserBehaviorMixin {

    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"
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