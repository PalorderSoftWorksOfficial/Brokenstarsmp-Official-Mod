package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlock_Mixin {

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void disableSpread(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ServerRules.DISABLE_FIRE_SPREAD) {
            ci.cancel();
        }
    }
}