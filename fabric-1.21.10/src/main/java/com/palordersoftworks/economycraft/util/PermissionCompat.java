package com.palordersoftworks.economycraft.util;

import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

public final class PermissionCompat {

    private PermissionCompat() {}

    public static Predicate<ServerCommandSource> gamemaster() {
        return source -> {
            if (source.getPlayer() == null) {
                return true;
            }

            return source.hasPermissionLevel(2);
        };
    }

    public static ServerCommandSource withOwnerPermission(ServerCommandSource source) {
        return source;
    }
}