package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilBlock.class)
public class AnvilBlock_Mixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private static void brokenstarsmpmod$preventDamage(BlockState fallingState, CallbackInfoReturnable<BlockState> cir) {
        if (!ServerRules.PREVENT_ANVIL_DAMAGE) return;
        cir.setReturnValue(fallingState);
    }
}