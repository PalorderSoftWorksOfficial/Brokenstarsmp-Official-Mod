package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntity_Mixin {

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void incrementHoney(World world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity, CallbackInfo ci) {
        int honey = state.get(BeehiveBlock.HONEY_LEVEL);
        if (honey < 5) {
            world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, honey + 1), 3);
        }
    }
}