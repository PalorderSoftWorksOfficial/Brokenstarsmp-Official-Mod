package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntity_Mixin {

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void incrementHoney(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity, CallbackInfo ci) {
        int increment = ServerRules.BEEHIVE_HONEY_INCREMENT;
        int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);
        if (honey < 5) {
            world.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, Math.min(honey + increment, 5)), 3);
        }
    }
}