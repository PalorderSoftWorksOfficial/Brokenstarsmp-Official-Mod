package com.palordersoftworks.brokenstarsmpmod.initiaters;

import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.DROP_AT_FEET_RADIUS;

public class DropAtFeet implements ModInitializer {



    @Override
    public void onInitialize() {
        ConfigManager.registerAnnotatedConfigs(ServerRules.class);
        ConfigManager.registerCommands();
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ItemEntity item)) return;
            if (!(world instanceof ServerWorld serverWorld)) return;

            double ix = item.getX();
            double iy = item.getY();
            double iz = item.getZ();
            int r = DROP_AT_FEET_RADIUS;

            for (PlayerEntity player : serverWorld.getPlayers()) {
                double dx = Math.abs(player.getX() - ix);
                double dy = Math.abs(player.getY() - iy);
                double dz = Math.abs(player.getZ() - iz);

                if (dx <= r && dy <= r && dz <= r) {
                    item.updatePosition(player.getX(), player.getY(), player.getZ());
                    item.setVelocity(0, 0, 0);
                    return;
                }
            }
        });
    }
}