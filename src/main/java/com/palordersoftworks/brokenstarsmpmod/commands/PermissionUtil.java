package com.palordersoftworks.brokenstarsmpmod.commands;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

import static com.palordersoftworks.brokenstarsmpmod.commands.LinkFishingRod.*;

public class PermissionUtil {
    private static final String IMMORTAL_PERMISSION = "unstablesmp.immortal";

    public static boolean isOwnerOrDev(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;

        return player.getUuid().equals(OWNER_UUID)
                || player.getUuid().equals(OWNER_UUID2)
                || player.getUuid().equals(DEV_UUID);
    }

    public static boolean hasImmortalPermission(ServerCommandSource source) {
        if (source.hasPermissionLevel(4)) {
            return true;
        }

        ServerPlayerEntity player = source.getPlayer();
        return player != null && hasImmortalPermission(player);
    }

    public static boolean hasImmortalPermission(ServerPlayerEntity player) {
        boolean fallback = player.getServer() != null && player.getServer().getPlayerManager().isOperator(player.getGameProfile());
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method check = permissionsClass.getMethod("check", ServerPlayerEntity.class, String.class, boolean.class);
            Object result = check.invoke(null, player, IMMORTAL_PERMISSION, fallback);
            if (result instanceof Boolean value) {
                return value;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        return fallback;
    }
}
