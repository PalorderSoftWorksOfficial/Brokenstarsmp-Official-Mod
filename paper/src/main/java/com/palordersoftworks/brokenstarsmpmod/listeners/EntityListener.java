package com.palordersoftworks.brokenstarsmpmod.listeners;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

public class EntityListener implements Listener {

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (ServerRules.DROP_AT_FEET_RADIUS <= 0) {
            return;
        }

        Item item = event.getEntity();
        Location loc = item.getLocation();
        int radius = ServerRules.DROP_AT_FEET_RADIUS;

        for (Player player : item.getWorld().getPlayers()) {
            double dx = Math.abs(player.getLocation().getX() - loc.getX());
            double dy = Math.abs(player.getLocation().getY() - loc.getY());
            double dz = Math.abs(player.getLocation().getZ() - loc.getZ());

            if (dx <= radius && dy <= radius && dz <= radius) {
                item.teleport(player.getLocation());
                item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                return;
            }
        }
    }
}
