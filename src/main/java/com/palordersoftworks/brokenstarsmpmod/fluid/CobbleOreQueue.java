package com.palordersoftworks.brokenstarsmpmod.fluid;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class CobbleOreQueue {
    private CobbleOreQueue() {}

    private record Entry(Level world, BlockPos pos) {}

    private static final Queue<Entry> QUEUE = new ArrayDeque<>();

    public static void enqueue(Level world, BlockPos pos) {
        QUEUE.add(new Entry(world, pos.immutable()));
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