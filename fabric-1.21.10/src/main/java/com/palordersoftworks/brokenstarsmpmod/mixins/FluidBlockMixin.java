package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidBlock.class)
public abstract class FluidBlockMixin {

    @Inject(method = "receiveNeighborFluids", at = @At("HEAD"))
    private static void brokenstarsmpmod$queueFluidPos(
            World world,
            BlockPos blockPos,
            BlockState blockState,
            CallbackInfoReturnable<Boolean> cir
    ) {
        CobbleOreQueue.enqueue(world, blockPos);
    }
}