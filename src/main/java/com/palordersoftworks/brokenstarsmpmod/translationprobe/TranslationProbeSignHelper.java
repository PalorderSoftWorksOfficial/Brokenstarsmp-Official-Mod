package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

/**
 * Invisible sign placement helpers mirroring CheckHacks {@code SignUtil}.
 */
public final class TranslationProbeSignHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TranslationProbeSignHelper() {}

    public static BlockPos findAirNear(ServerPlayerEntity player) {
        BlockPos base = player.getBlockPos();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        int[][] offsets = {
                {0, 1, 0}, {0, 2, 0}, {0, 3, 0}, {0, 4, 0}, {0, 5, 0},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2},
        };
        for (int[] offset : offsets) {
            BlockPos pos = base.add(offset[0], offset[1], offset[2]);
            if (world.getBlockState(pos).isAir()) {
                return pos.toImmutable();
            }
        }
        return null;
    }

    public static void sendSignPackets(ServerPlayerEntity player, SignBlockEntity sign, BlockPos signPos) {
        try {
            player.networkHandler.sendPacket(BlockEntityUpdateS2CPacket.create(sign));
            player.networkHandler.sendPacket(new SignEditorOpenS2CPacket(signPos, true));
            BlockState air = Blocks.AIR.getDefaultState();
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(signPos, air));
        } catch (Exception e) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] sign packet send failed for {}: {}",
                    player.getName().getString(), e.getMessage());
            player.openEditSignScreen(sign, true);
        }
    }
}
