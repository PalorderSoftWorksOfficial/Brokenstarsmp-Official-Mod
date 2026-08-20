package com.palordersoftworks.brokenstarsmpmod.unstablesmp;

import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
            ServerPlayer player = handler.player;
            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(player, Component.translatable("multiplayer.player.joined", player.getDisplayName()));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(player, Component.translatable("multiplayer.player.left", player.getDisplayName()));
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer victim)) {
                return;
            }

            if (UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
                sendNearbyMessage(victim, Component.translatable("death.attack.generic", victim.getDisplayName()));
            }

            if (UnstableSMPRules.WITHER_SOUND_ENABLED) {
                playWitherSound(victim);
            }

            if (UnstableSMPRules.DEATH_BAN_ENABLED) {
                banAndKick(victim);
            }
        });
    }

    public static boolean isImmortal(ServerPlayer player) {
        return player.entityTags().contains(IMMORTAL_TAG);
    }

    public static void setImmortal(ServerPlayer player, boolean immortal) {
        if (immortal) {
            player.addTag(IMMORTAL_TAG);
        } else {
            player.removeTag(IMMORTAL_TAG);
        }
    }

    public static void sendNearbyMessage(ServerPlayer origin, Component message) {
        if (!UnstableSMPRules.PROXIMITY_MESSAGES_ENABLED) {
            return;
        }

        ServerLevel world = (ServerLevel) origin.level();
        double radius = Math.max(0, UnstableSMPRules.PROXIMITY_MESSAGES_DISTANCE);
        double radiusSq = radius * radius;

        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(origin) > radiusSq) {
                continue;
            }
            player.connection.send(new ClientboundSystemChatPacket(message, false));
        }
    }
    // Okay to whatever made this to do duel i fucking hate you
    public static void playWitherSound(ServerPlayer victim) {
        ServerLevel world = (ServerLevel) victim.level();
        float volume = Math.max(1.0F, UnstableSMPRules.WITHER_SOUND_DISTANCE / 16.0F);
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, volume, 1.0F);
    }

    public static void banAndKick(ServerPlayer victim) {
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }

        String reason = UnstableSMPRules.DEATH_BAN_REASON;
        String escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ban " + victim.getGameProfile().name() + " \"" + escaped + "\"");
        victim.connection.disconnect(Component.literal(reason));
    }
}