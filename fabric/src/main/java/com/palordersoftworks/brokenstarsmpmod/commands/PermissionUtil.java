package com.palordersoftworks.brokenstarsmpmod.commands;

import com.mojang.logging.LogUtils;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.palordersoftworks.brokenstarsmpmod.permissions.PermissionNodes;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;

public class PermissionUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
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

        return PermissionCompat.has(player, PermissionNodes.IMMORTAL);
    }
}
