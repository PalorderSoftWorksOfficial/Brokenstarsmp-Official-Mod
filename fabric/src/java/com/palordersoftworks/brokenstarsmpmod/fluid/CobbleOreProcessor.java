package com.palordersoftworks.brokenstarsmpmod.fluid;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class CobbleOreProcessor {
    private static final Block[] ORE_POOL = new Block[]{
            Blocks.COAL_ORE,
            Blocks.COPPER_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.NETHER_GOLD_ORE
    };

    private CobbleOreProcessor() {
    }

    public static void process(net.minecraft.world.level.Level world, BlockPos pos) {
        if (!ServerRules.RANDOM_ORE_COBBLESTONE) {
            return;
        }
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        if (!serverWorld.hasChunkAt(pos)) {
            return;
        }
        if (!serverWorld.getBlockState(pos).is(Blocks.COBBLESTONE)) {
            return;
        }

        Block ore = ORE_POOL[ThreadLocalRandom.current().nextInt(ORE_POOL.length)];
        serverWorld.setBlock(pos, ore.defaultBlockState(), 3);
    }
}
