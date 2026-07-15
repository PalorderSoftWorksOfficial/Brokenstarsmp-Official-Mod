package com.palordersoftworks.brokenstarsmpmod.unstablesmp;

import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UnstableSMPFeatures {
    public static final String IMMORTAL_TAG = "unstablesmp_immortal";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private UnstableSMPFeatures() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(player, Text.translatable("multiplayer.player.joined", player.getDisplayName()));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(player, Text.translatable("multiplayer.player.left", player.getDisplayName()));
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) {
                return;
            }

            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(victim, Text.translatable("death.attack.generic", victim.getDisplayName()));
            }

            if (UnstableSMPRules.WITHER_SOUND_ENABLED) {
                playWitherSound(victim);
            }

            if (UnstableSMPRules.DEATH_BAN_ENABLED) {
                banAndKick(victim);
            }
        });
    }

    public static boolean isImmortal(ServerPlayerEntity player) {
        return player.getCommandTags().contains(IMMORTAL_TAG);
    }

    public static void setImmortal(ServerPlayerEntity player, boolean immortal) {
        if (immortal) {
            player.addCommandTag(IMMORTAL_TAG);
        } else {
            player.removeCommandTag(IMMORTAL_TAG);
        }
    }

    public static void sendNearbyMessage(ServerPlayerEntity origin, Text message) {
        if (!UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            return;
        }

        ServerWorld world = (ServerWorld) origin.getEntityWorld();
        double radius = Math.max(0, UnstableSMPRules.PROXIMITY_MESSAGES_DISTANCE);
        double radiusSq = radius * radius;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(origin) > radiusSq) {
                continue;
            }
            player.networkHandler.sendPacket(new GameMessageS2CPacket(message, false));
        }
    }

    public static void playWitherSound(ServerPlayerEntity victim) {
        ServerWorld world = (ServerWorld) victim.getEntityWorld();
        float volume = Math.max(1.0F, UnstableSMPRules.WITHER_SOUND_DISTANCE / 16.0F);
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, volume, 1.0F);
    }

    public static void banAndKick(ServerPlayerEntity victim) {
        MinecraftServer server = victim.getEntityWorld().getServer();
        if (server == null) {
            return;
        }

        String reason = UnstableSMPRules.DEATH_BAN_REASON;
        String escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"");
        server.getCommandManager().parseAndExecute(server.getCommandSource(), "ban " + victim.getGameProfile().name() + " \"" + escaped + "\"");
        victim.networkHandler.disconnect(Text.literal(reason));
    }
}