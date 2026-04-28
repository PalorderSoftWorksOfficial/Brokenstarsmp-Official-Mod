package com.palordersoftworks.brokenstarsmpmod.fluid;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

public final class CobbleOreProcessor {
    private CobbleOreProcessor() {}

    private static final Block[] ORE_POOL = new Block[] {
            Blocks.COAL_ORE,
            Blocks.COPPER_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.NETHER_GOLD_ORE
    };

    public static void process(net.minecraft.world.World world, BlockPos pos) {
        if (!ServerRules.RANDOM_ORE_COBBLESTONE) return;
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!serverWorld.isChunkLoaded(pos)) return;

        if (!serverWorld.getBlockState(pos).isOf(Blocks.COBBLESTONE)) return;

        Block ore = ORE_POOL[ThreadLocalRandom.current().nextInt(ORE_POOL.length)];
        serverWorld.setBlockState(pos, ore.getDefaultState(), 3);
    }
}