package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public class ChestBlock_Mixin {

    @Inject(method = "isChestBlockedAt", at = @At("HEAD"), cancellable = true)
    private static void allowChestOpening(LevelAccessor world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ServerRules.ALLOW_CHEST_OPENING) {
            cir.setReturnValue(false);
        }
    }
}