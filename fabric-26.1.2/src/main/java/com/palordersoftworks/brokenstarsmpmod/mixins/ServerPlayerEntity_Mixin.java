package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntity_Mixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void removeFakePlayerInvulnerability(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class<?> fakeClass = Class.forName("carpet.patches.EntityPlayerMPFake");
            if (fakeClass.isInstance(this)) {
                cir.setReturnValue(false);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }
}
