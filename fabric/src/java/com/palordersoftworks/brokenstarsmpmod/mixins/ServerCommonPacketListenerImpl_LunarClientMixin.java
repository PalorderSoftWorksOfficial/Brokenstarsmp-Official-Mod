package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.messages.LunarClientBlocker;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImpl_LunarClientMixin {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmp$blockLunarClient(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof BrandPayload brandPayload) {
            ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) (Object) this;
            if (LunarClientBlocker.block(listener, brandPayload)) {
                ci.cancel();
            }
        }
    }
}