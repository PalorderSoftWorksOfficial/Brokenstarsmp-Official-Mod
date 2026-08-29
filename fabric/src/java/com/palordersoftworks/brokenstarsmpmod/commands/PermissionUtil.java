package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;

public class PermissionUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String IMMORTAL_PERMISSION = "unstablesmp.immortal";
    // TODO: Set this to the actual owner UUID. Username-based checks are insecure
    // because Minecraft usernames can change. Override via system property if needed.
    private static final UUID OWNER_UUID = UUID.fromString(
            System.getProperty("brokenstarsmp.owner-uuid", "00000000-0000-0000-0000-000000000000"));

    public static boolean isOwnerOrDev(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        return player.getUUID().equals(OWNER_UUID);
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
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("Fabric Permissions API not available, falling back to op check", e);
        }

        return fallback;
    }
}
