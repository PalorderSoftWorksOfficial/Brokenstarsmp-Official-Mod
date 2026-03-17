package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilBlock.class)
public class AnvilBlock_Mixin {

    @Inject(method = "getLandingState", at = @At("HEAD"), cancellable = true)
    private static void brokenstarsmpmod$preventDamage(BlockState fallingState, CallbackInfoReturnable<BlockState> cir) {
        if (!ServerRules.PREVENT_ANVIL_DAMAGE) return;
        cir.setReturnValue(fallingState);
    }
}