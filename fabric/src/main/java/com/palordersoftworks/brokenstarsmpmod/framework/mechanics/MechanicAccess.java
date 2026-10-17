package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import com.palordersoftworks.brokenstarsmpmod.framework.BrokenStarsFramework;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MechanicAccess {
    private MechanicAccess() {
    }

    public static boolean bool(String id, MechanicContext context, boolean fallback) {
        try {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return fallback;
            }
            Object value = framework.read(id, context).value();
            return value instanceof Boolean booleanValue ? booleanValue : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static int integer(String id, MechanicContext context, int fallback) {
        try {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return fallback;
            }
            Object value = framework.read(id, context).value();
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static double number(String id, MechanicContext context, double fallback) {
        try {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return fallback;
            }
            Object value = framework.read(id, context).value();
            return value instanceof Number number ? number.doubleValue() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static MechanicContext global() {
        return MechanicContext.global();
    }

    public static MechanicContext level(ServerLevel level) {
        if (level == null) {
            return global();
        }
        return MechanicContext.builder().dimension(level.dimension().identifier().toString()).build();
    }

    public static MechanicContext player(ServerPlayer player) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework == null ? global() : framework.context(player);
    }
}
