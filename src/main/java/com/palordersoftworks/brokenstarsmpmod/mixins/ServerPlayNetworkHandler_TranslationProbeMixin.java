package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeController;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandler_TranslationProbeMixin {

    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmp$translationProbeInterceptSign(UpdateSignC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        if (TranslationProbeController.tryConsumeSignPacket(handler.getPlayer(), packet)) {
            ci.cancel();
        }
    }
}
