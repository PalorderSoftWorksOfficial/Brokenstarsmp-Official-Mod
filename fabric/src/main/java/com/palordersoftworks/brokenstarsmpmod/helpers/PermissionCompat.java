package com.palordersoftworks.brokenstarsmpmod.helpers;

import com.palordersoftworks.brokenstarsmpmod.framework.permissions.LpHook;
import com.palordersoftworks.brokenstarsmpmod.permissions.PermissionNodes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class PermissionCompat {
    private PermissionCompat() {
    }

    public static Predicate<CommandSourceStack> gamemaster() {
        return source -> has(source, PermissionNodes.GAMEMASTER);
    }

    public static Predicate<CommandSourceStack> require(String node) {
        return source -> has(source, node);
    }

    public static Predicate<CommandSourceStack> requireAny(String... nodes) {
        return source -> hasAny(source, nodes);
    }

    public static boolean has(CommandSourceStack source, String node) {
        if (source == null || source.getPlayer() == null) {
            return true;
        }
        if (hasNode(source.getPlayer(), node)) {
            return true;
        }
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }

    public static boolean has(ServerPlayer player, String node) {
        if (player == null) {
            return false;
        }
        return hasNode(player, node) || player.level().getServer().getPlayerList().isOp(player.nameAndId());
    }

    public static boolean hasAny(CommandSourceStack source, String... nodes) {
        if (source == null || source.getPlayer() == null) {
            return true;
        }
        for (String node : nodes) {
            if (has(source, node)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPermissionNode(ServerPlayer player, String node) {
        return hasNode(player, node);
    }

    public static boolean hasPermissionNode(UUID player, String node) {
        return hasNode(player, node);
    }

    public static Optional<String> luckPermsMeta(UUID player, String key) {
        return LpHook.meta(player, key);
    }

    public static Optional<String> primaryGroup(UUID player) {
        return LpHook.primaryGroup(player);
    }

    public static Optional<String> prefix(UUID player) {
        return LpHook.prefix(player);
    }

    public static Optional<String> suffix(UUID player) {
        return LpHook.suffix(player);
    }

    public static boolean luckPermsAvailable() {
        return LpHook.available();
    }

    public static int clampedInt(Optional<String> value, int fallback, int minimum, int maximum) {
        if (value.isEmpty()) {
            return Math.max(minimum, Math.min(maximum, fallback));
        }
        try {
            long parsed = Long.parseLong(value.get().trim());
            if (parsed < minimum || parsed > maximum) {
                return Math.max(minimum, Math.min(maximum, fallback));
            }
            return (int) parsed;
        } catch (NumberFormatException exception) {
            return Math.max(minimum, Math.min(maximum, fallback));
        }
    }

    private static boolean hasNode(ServerPlayer player, String node) {
        if (node == null || node.isBlank()) {
            return false;
        }
        if (LpHook.available()) {
            return LpHook.check(player.getUUID(), node);
        }
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Object result = permissionsClass.getMethod("check", ServerPlayer.class, String.class, boolean.class)
                    .invoke(null, player, node, false);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasNode(UUID player, String node) {
        if (player == null || node == null || node.isBlank()) {
            return false;
        }
        return LpHook.available() && LpHook.check(player, node);
    }
}
