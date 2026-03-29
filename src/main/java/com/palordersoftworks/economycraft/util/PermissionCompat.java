package com.palordersoftworks.economycraft.util;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

public final class PermissionCompat {

    private PermissionCompat() {}

    public static Predicate<ServerCommandSource> gamemaster() {
        Permission.Level required = new Permission.Level(PermissionLevel.GAMEMASTERS);
        return source -> {
            if (source.getPlayer() == null) {
                return true;
            }
            return source.getPermissions().hasPermission(required);
        };
    }

    public static ServerCommandSource withOwnerPermission(ServerCommandSource source) {
        return source;
    }
}
