package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlock.class)
public abstract class FluidBlockMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void brokenstarsmpmod$queueNearbyFluidPositions(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CobbleOreQueue.enqueue(world, pos);
        CobbleOreQueue.enqueue(world, pos.above());
        CobbleOreQueue.enqueue(world, pos.below());
        CobbleOreQueue.enqueue(world, pos.north());
        CobbleOreQueue.enqueue(world, pos.south());
        CobbleOreQueue.enqueue(world, pos.east());
        CobbleOreQueue.enqueue(world, pos.west());
    }
}
