package com.palordersoftworks.brokenstarsmpmod.listeners;

import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (UnstableSMPRules.IMMORTAL_SYSTEM_ENABLED && UnstableSMPFeatures.isImmortal(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            return;
        }
        Player player = event.getPlayer();
        Component message = Component.translatable("multiplayer.player.joined", player.name());
        UnstableSMPFeatures.sendNearbyMessage(player, message);
        event.joinMessage(null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            return;
        }
        Player player = event.getPlayer();
        Component message = Component.translatable("multiplayer.player.left", player.name());
        UnstableSMPFeatures.sendNearbyMessage(player, message);
        event.quitMessage(null);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            Component message = Component.translatable("death.attack.generic", victim.name());
            UnstableSMPFeatures.sendNearbyMessage(victim, message);
            event.deathMessage(null);
        }

        if (UnstableSMPRules.WITHER_SOUND_ENABLED) {
            UnstableSMPFeatures.playWitherSound(victim);
        }

        if (UnstableSMPRules.DEATH_BAN_ENABLED) {
            UnstableSMPFeatures.banAndKick(victim);
        }
    }
}
