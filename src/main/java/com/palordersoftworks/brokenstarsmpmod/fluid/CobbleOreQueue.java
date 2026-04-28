package com.palordersoftworks.brokenstarsmpmod.fluid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Queue;

public final class CobbleOreQueue {
    private CobbleOreQueue() {}

    private record Entry(World world, BlockPos pos) {}

    private static final Queue<Entry> QUEUE = new ArrayDeque<>();

    public static void enqueue(World world, BlockPos pos) {
        QUEUE.add(new Entry(world, pos.toImmutable()));
    }

    public static void process() {
        while (!QUEUE.isEmpty()) {
            Entry entry = QUEUE.poll();
            if (entry != null) {
                CobbleOreProcessor.process(entry.world(), entry.pos());
            }
        }
    }
}