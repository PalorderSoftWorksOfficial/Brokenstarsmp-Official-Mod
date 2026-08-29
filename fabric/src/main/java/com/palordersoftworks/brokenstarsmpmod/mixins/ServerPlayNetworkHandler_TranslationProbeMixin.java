package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeController;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandler_TranslationProbeMixin {

    @Inject(method = "handleSignUpdate(Lnet/minecraft/network/protocol/game/ServerboundSignUpdatePacket;)V", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmp$translationProbeInterceptSign(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
        if (TranslationProbeController.tryConsumeSignPacket(handler.getPlayer(), packet)) {
            ci.cancel();
        }
    }
}
