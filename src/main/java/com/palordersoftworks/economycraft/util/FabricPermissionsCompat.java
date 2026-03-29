package com.palordersoftworks.economycraft.util;

import net.minecraft.server.command.ServerCommandSource;

/**
 * Optional Fabric Permissions API (LuckPerms hooks into this on Fabric).
 */
public final class FabricPermissionsCompat {
    private FabricPermissionsCompat() {}

    public static boolean check(ServerCommandSource source, String permissionNode, boolean defaultIfApiMissing) {
        try {
            Class<?> perm = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return (boolean) perm.getMethod("check", ServerCommandSource.class, String.class, boolean.class)
                    .invoke(null, source, permissionNode, defaultIfApiMissing);
        } catch (ClassNotFoundException e) {
            return defaultIfApiMissing;
        } catch (Throwable ignored) {
            return defaultIfApiMissing;
        }
    }
}
