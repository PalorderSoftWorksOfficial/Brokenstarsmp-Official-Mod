package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DropperBlock.class)
public abstract class DropperBlock_Mixin {

    @Unique
    private int brokenstarsmpmod$getAmount() {
        int ruleAmount = ServerRules.DISPENSER_DROP_AMOUNT;
        return Math.max(1, ruleAmount);
    }

    @Redirect(
            method = "dispense",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;copyWithCount(I)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack brokenstarsmpmod$copyWithCustomCount(ItemStack stack, int amount) {
        return stack.copyWithCount(brokenstarsmpmod$getAmount());
    }

    @Redirect(
            method = "dispense",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;decrement(I)V"
            )
    )
    private void brokenstarsmpmod$decrementByCustomCount(ItemStack stack, int amount) {
        stack.decrement(brokenstarsmpmod$getAmount());
    }
}