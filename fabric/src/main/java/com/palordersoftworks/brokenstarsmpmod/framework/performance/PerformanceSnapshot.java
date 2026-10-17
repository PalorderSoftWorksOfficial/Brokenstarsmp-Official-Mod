package com.palordersoftworks.brokenstarsmpmod.framework.performance;

public record PerformanceSnapshot(
        int players,
        int worlds,
        int entities,
        int mobs,
        int itemEntities,
        int blockEntities
) {
}
