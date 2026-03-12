package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntity_Mixin {

    @Inject(method = "getCookTime", at = @At("HEAD"), cancellable = true)
    private static void brokenstarsmpmod$fastSmelting(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(1);
    }
}