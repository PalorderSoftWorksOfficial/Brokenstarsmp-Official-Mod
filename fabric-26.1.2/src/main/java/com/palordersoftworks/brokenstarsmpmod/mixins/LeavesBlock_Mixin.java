package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LeavesBlock.class)
public abstract class LeavesBlock_Mixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void disableDecay(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ServerRules.DISABLE_LEAF_DECAY) {
            ci.cancel();
        }
    }
}