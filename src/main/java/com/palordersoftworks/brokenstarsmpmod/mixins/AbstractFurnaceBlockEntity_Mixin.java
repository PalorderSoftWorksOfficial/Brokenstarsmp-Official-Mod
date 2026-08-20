package com.palordersoftworks.brokenstarsmpmod.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FURNACE_COOKING_SPEED;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntity_Mixin {

    @Inject(method = "getTotalCookTime", at = @At("HEAD"), cancellable = true)
    private static void brokenstarsmpmod$configurableCookingSpeed(
            ServerLevel world,
            AbstractFurnaceBlockEntity furnace,
            CallbackInfoReturnable<Integer> cir
    ) {
        int speed = FURNACE_COOKING_SPEED;
        if (speed <= 0) speed = 1;
        cir.setReturnValue(speed);
    }
}