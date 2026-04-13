package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public class ChestBlock_Mixin {

    @Inject(method = "isChestBlocked", at = @At("HEAD"), cancellable = true)
    private static void allowChestOpening(WorldAccess world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ServerRules.ALLOW_CHEST_OPENING) {
            cir.setReturnValue(false);
        }
    }
}