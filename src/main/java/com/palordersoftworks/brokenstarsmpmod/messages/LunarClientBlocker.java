package com.palordersoftworks.brokenstarsmpmod.messages;

import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.util.Locale;
import java.util.Map;

public final class LunarClientBlocker {
    private static final String REASON = "Disallowed Mods: Lunar Client";

    private LunarClientBlocker() {
    }

    public static boolean block(ServerCommonPacketListenerImpl listener, BrandPayload payload) {
        if (!isLunarClient(payload.brand())) {
            return false;
        }

        Messages.initialize();
        listener.disconnect(MiniMessageApi.toNative(
                Messages.render("kickScreen", Map.of("reason", REASON))
        ));
        return true;
    }

    public static boolean isLunarClient(String brand) {
        if (brand == null) {
            return false;
        }

        String normalized = brand.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return normalized.equals("lunarclient") || normalized.contains("lunarclient");
    }
}