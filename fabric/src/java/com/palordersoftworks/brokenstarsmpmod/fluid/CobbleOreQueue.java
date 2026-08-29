package com.palordersoftworks.brokenstarsmpmod.fluid;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class CobbleOreQueue {
    private static final int MAX_PER_TICK = 2048;
    private static final Set<Entry> QUEUE = new LinkedHashSet<>();

    private CobbleOreQueue() {
    }

    private record Entry(Level world, BlockPos pos) {
    }

    public static void enqueue(Level world, BlockPos pos) {
        if (world.isClientSide()) {
            return;
        }
        QUEUE.add(new Entry(world, pos.immutable()));
    }

    public static void process() {
        int processed = 0;
        while (!QUEUE.isEmpty() && processed++ < MAX_PER_TICK) {
            Entry entry = QUEUE.iterator().next();
            QUEUE.remove(entry);
            CobbleOreProcessor.process(entry.world(), entry.pos());
        }
    }
}
