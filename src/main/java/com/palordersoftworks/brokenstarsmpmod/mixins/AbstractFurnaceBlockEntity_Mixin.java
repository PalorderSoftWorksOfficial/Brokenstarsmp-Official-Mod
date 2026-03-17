package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.Rule;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FURNACE_COOKING_SPEED;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntity_Mixin {

    @Inject(method = "getCookTime", at = @At("HEAD"), cancellable = true)
    private static void brokenstarsmpmod$configurableCookingSpeed(CallbackInfoReturnable<Integer> cir) {
        int speed = FURNACE_COOKING_SPEED;
        if (speed <= 0) speed = 1;
        cir.setReturnValue(speed);
    }
}