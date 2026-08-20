package com.palordersoftworks.brokenstarsmpmod.helpers;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class PermissionCompat {

    private PermissionCompat() {}

    public static Predicate<CommandSourceStack> gamemaster() {
        Permission.HasCommandLevel required = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
        return source -> {
            if (source.getPlayer() == null) {
                return true;
            }
            return source.permissions().hasPermission(required);
        };
    }
}
