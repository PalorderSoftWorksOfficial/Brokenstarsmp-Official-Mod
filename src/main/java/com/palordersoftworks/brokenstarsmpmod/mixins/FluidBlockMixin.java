package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public abstract class FluidBlockMixin {

    @Inject(method = "shouldSpreadLiquid", at = @At("HEAD"))
    private void brokenstarsmpmod$queueFluidPos(
            Level world,
            BlockPos blockPos,
            BlockState blockState,
            CallbackInfoReturnable<Boolean> cir
    ) {
        CobbleOreQueue.enqueue(world, blockPos);
    }
}