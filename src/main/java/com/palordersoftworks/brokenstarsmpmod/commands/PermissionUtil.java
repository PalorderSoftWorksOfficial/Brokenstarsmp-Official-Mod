package com.palordersoftworks.brokenstarsmpmod.commands;

import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

public class PermissionUtil {
    private static final String IMMORTAL_PERMISSION = "unstablesmp.immortal";

    public static boolean isOwnerOrDev(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;

        return player.getName().equals("AdoreKittens");
    }

    public static boolean hasImmortalPermission(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            return false;
        }

        return hasImmortalPermission(player);
    }

    public static boolean hasImmortalPermission(ServerPlayerEntity player) {
        PlayerConfigEntry entry = player.getPlayerConfigEntry();

        boolean fallback = player.getEntityWorld()
                .getServer()
                .getPlayerManager()
                .isOperator(entry);

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
