package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.fakes.RedstoneWireBlockInterface;
import com.palordersoftworks.brokenstarsmpmod.helpers.RedstoneWireTurbo;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireBlock_fastMixin implements RedstoneWireBlockInterface {

    @Unique private RedstoneWireTurbo wireTurbo;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onCtor(AbstractBlock.Settings settings, CallbackInfo ci) {
        this.wireTurbo = new RedstoneWireTurbo((RedstoneWireBlock) (Object) this);
    }

    @Inject(method = "neighborUpdate", at = @At("TAIL"))
    private void afterNeighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        wireTurbo.updateSurroundingRedstone(world, pos, state, null);
    }

    @Inject(method = "onBlockAdded", at = @At("TAIL"))
    private void afterOnBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        wireTurbo.updateSurroundingRedstone(world, pos, state, null);
    }

    @Inject(method = "onStateReplaced", at = @At("TAIL"))
    private void afterOnStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved, CallbackInfo ci) {
        wireTurbo.updateSurroundingRedstone(world, pos, state, null);
    }
}