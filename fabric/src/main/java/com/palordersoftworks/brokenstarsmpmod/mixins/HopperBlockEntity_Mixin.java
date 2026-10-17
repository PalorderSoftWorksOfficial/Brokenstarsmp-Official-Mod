package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicAccess;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntity_Mixin {

    @ModifyConstant(method = "tryMoveItems", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 8))
    private static int modifyCooldown(int original) {
        return MechanicAccess.integer("hopper_cooldown", MechanicAccess.global(), ServerRules.HOPPER_TRANSFER_COOLDOWN);
    }
}