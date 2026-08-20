package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Invisible sign placement helpers mirroring CheckHacks {@code SignUtil}.
 */
public final class TranslationProbeSignHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TranslationProbeSignHelper() {}

    public static BlockPos findAirNear(ServerPlayer player) {
        BlockPos base = player.blockPosition();
        ServerLevel world = (ServerLevel) player.level();
        int[][] offsets = {
                {0, 1, 0}, {0, 2, 0}, {0, 3, 0}, {0, 4, 0}, {0, 5, 0},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2},
        };
        for (int[] offset : offsets) {
            BlockPos pos = base.offset(offset[0], offset[1], offset[2]);
            if (world.getBlockState(pos).isAir()) {
                return pos.immutable();
            }
        }
        return null;
    }

    public static void sendSignPackets(ServerPlayer player, SignBlockEntity sign, BlockPos signPos) {
        try {
            player.connection.send(ClientboundBlockEntityDataPacket.create(sign));
            player.connection.send(new ClientboundOpenSignEditorPacket(signPos, true));
            BlockState air = Blocks.AIR.defaultBlockState();
            player.connection.send(new ClientboundBlockUpdatePacket(signPos, air));
        } catch (Exception e) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] sign packet send failed for {}: {}",
                    player.getName().getString(), e.getMessage());
            player.openTextEdit(sign, true);
        }
    }
}
