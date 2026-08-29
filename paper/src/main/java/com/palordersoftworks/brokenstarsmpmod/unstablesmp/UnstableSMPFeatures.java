package com.palordersoftworks.brokenstarsmpmod.unstablesmp;

import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UnstableSMPFeatures {
    public static final String IMMORTAL_TAG = "unstablesmp_immortal";
    private static final Set<UUID> IMMORTAL_PLAYERS = ConcurrentHashMap.newKeySet();

    private UnstableSMPFeatures() {
    }

    public static boolean isImmortal(Player player) {
        return IMMORTAL_PLAYERS.contains(player.getUniqueId());
    }

    public static void setImmortal(Player player, boolean immortal) {
        if (immortal) {
            IMMORTAL_PLAYERS.add(player.getUniqueId());
        } else {
            IMMORTAL_PLAYERS.remove(player.getUniqueId());
        }
    }

    public static void sendNearbyMessage(Player origin, Component message) {
        if (!UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            return;
        }

        double radius = Math.max(0, UnstableSMPRules.PROXIMITY_MESSAGES_DISTANCE);
        double radiusSq = radius * radius;

        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin.getLocation()) > radiusSq) {
                continue;
            }
            player.sendMessage(message);
        }
    }

    public static void playWitherSound(Player victim) {
        float volume = Math.max(1.0F, (float) UnstableSMPRules.WITHER_SOUND_DISTANCE / 16.0F);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, volume, 1.0F);
    }

    public static void banAndKick(Player victim) {
        String reason = UnstableSMPRules.DEATH_BAN_REASON;
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(victim.getName(), reason, null, "BrokenstarSMP");
        victim.kick(Component.text(reason));
    }
}
