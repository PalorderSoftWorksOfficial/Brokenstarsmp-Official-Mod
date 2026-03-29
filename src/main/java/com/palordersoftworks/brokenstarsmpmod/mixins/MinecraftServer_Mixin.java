package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_Mixin {

    @Inject(method = "getServerModName", at = @At("HEAD"), cancellable = true)
    private void overrideBrand(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("PalorderCentral");
    }
}