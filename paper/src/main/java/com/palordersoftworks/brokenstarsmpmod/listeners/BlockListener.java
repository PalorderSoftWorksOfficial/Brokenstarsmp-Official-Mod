package com.palordersoftworks.brokenstarsmpmod.listeners;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;

public class BlockListener implements Listener {

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        if (ServerRules.DISABLE_FIRE_SPREAD) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (ServerRules.DISABLE_FIRE_SPREAD) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (ServerRules.DISABLE_LEAF_DECAY) {
            event.setCancelled(true);
        }
    }
}
