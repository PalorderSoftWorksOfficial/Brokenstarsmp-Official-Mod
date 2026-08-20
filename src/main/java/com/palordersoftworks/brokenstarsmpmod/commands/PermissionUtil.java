package com.palordersoftworks.brokenstarsmpmod.commands;

import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class PermissionUtil {
    private static final String IMMORTAL_PERMISSION = "unstablesmp.immortal";

    public static boolean isOwnerOrDev(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        return player.getName().equals("AdoreKittens");
    }

    public static boolean hasImmortalPermission(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            return false;
        }

        return hasImmortalPermission(player);
    }

    public static boolean hasImmortalPermission(ServerPlayer player) {
        NameAndId entry = player.nameAndId();

        boolean fallback = player.level()
                .getServer()
                .getPlayerList()
                .isOp(entry);

        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method check = permissionsClass.getMethod("check", ServerPlayer.class, String.class, boolean.class);
            Object result = check.invoke(null, player, IMMORTAL_PERMISSION, fallback);

            if (result instanceof Boolean value) {
                return value;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        return fallback;
    }
}
