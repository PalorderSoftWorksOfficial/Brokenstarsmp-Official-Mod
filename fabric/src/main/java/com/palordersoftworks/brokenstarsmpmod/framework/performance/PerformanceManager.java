package com.palordersoftworks.brokenstarsmpmod.framework.performance;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;

public final class PerformanceManager {
    private volatile PerformanceSnapshot latest = new PerformanceSnapshot(0, 0, 0, 0, 0, -1);
    private int sampleCountdown;

    public void tick(MinecraftServer server) {
        if (sampleCountdown-- > 0) {
            return;
        }
        sampleCountdown = 20;
        int entities = 0;
        int mobs = 0;
        int items = 0;
        int worlds = 0;
        for (ServerLevel level : server.getAllLevels()) {
            worlds++;
            for (Entity entity : level.getAllEntities()) {
                entities++;
                if (entity instanceof Mob) {
                    mobs++;
                }
                if (entity instanceof ItemEntity) {
                    items++;
                }
            }
        }
        latest = new PerformanceSnapshot(server.getPlayerCount(), worlds, entities, mobs, items, -1);
    }

    public PerformanceSnapshot snapshot() {
        return latest;
    }

    public double averageTickMillis() {
        return latestTickMillis;
    }

    public double estimatedTps() {
        double mspt = latestTickMillis;
        if (mspt <= 0.001) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / mspt);
    }

    public java.util.List<String> diagnostics() {
        PerformanceSnapshot snapshot = latest;
        return java.util.List.of(
                "players=" + snapshot.players(),
                "worlds=" + snapshot.worlds(),
                "entities=" + snapshot.entities(),
                "mobs=" + snapshot.mobs(),
                "item_entities=" + snapshot.itemEntities(),
                "block_entities=" + (snapshot.blockEntities() < 0 ? "unavailable" : snapshot.blockEntities()),
                "average_tick_ms=" + String.format(java.util.Locale.ROOT, "%.2f", latestTickMillis)
        );
    }

    private volatile double latestTickMillis;

    public void sampleTickTime(MinecraftServer server) {
        latestTickMillis = server.getAverageTickTimeNanos() / 1_000_000.0;
    }
}
