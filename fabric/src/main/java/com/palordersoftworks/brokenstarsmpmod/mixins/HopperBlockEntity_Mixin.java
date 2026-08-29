package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntity_Mixin {

    @ModifyConstant(method = "tryMoveItems", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 8))
    private static int modifyCooldown(int original) {
        return ServerRules.HOPPER_TRANSFER_COOLDOWN;
    }
}