package com.palordersoftworks.brokenstarsmpmod.commands;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static com.palordersoftworks.brokenstarsmpmod.commands.LinkFishingRod.*;

public class PermissionUtil {
    public static boolean isOwnerOrDev(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;

        return player.getUuid().equals(OWNER_UUID)
                || player.getUuid().equals(OWNER_UUID2)
                || player.getUuid().equals(DEV_UUID);
    }
}