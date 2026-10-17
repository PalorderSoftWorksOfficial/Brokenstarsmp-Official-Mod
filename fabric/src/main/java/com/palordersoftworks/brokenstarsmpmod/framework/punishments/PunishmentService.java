package com.palordersoftworks.brokenstarsmpmod.framework.punishments;

import com.mojang.logging.LogUtils;
import eu.pb4.banhammer.api.BanHammer;
import eu.pb4.banhammer.api.PunishmentData;
import eu.pb4.banhammer.api.PunishmentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PunishmentService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean detected;
    private static volatile boolean warned;

    private PunishmentService() {
    }

    public static boolean available() {
        if (!detected) {
            try {
                Class.forName("eu.pb4.banhammer.api.BanHammer");
                detected = true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                detected = false;
                if (!warned) {
                    warned = true;
                    LOGGER.info("[BrokenStarSMP] BanHammer not installed; punishments fall back to vanilla commands");
                }
            }
        }
        return detected;
    }

    public static boolean ban(MinecraftServer server, UUID targetId, String targetName,
                              String reason, CommandSourceStack source) {
        if (available()) {
            try {
                PunishmentData data = PunishmentData.create(targetId, targetName,
                        Component.literal(targetName), null, sourceOrServer(source, server), reason,
                        0L, PunishmentType.BAN);
                BanHammer.punish(data);
                return true;
            } catch (Throwable throwable) {
                LOGGER.warn("[BrokenStarSMP] BanHammer ban failed for {}; falling back to vanilla", targetName, throwable);
            }
        }
        return vanillaBan(server, targetId, reason);
    }

    public static boolean tempBan(MinecraftServer server, UUID targetId, String targetName,
                                  long durationTicks, String reason, CommandSourceStack source) {
        if (available()) {
            try {
                PunishmentData data = PunishmentData.create(targetId, targetName,
                        Component.literal(targetName), null, sourceOrServer(source, server), reason,
                        durationTicks * 50L, PunishmentType.BAN);
                BanHammer.punish(data);
                return true;
            } catch (Throwable throwable) {
                LOGGER.warn("[BrokenStarSMP] BanHammer tempban failed for {}; falling back to vanilla", targetName, throwable);
            }
        }
        return vanillaTempBan(server, targetId, reason, durationTicks);
    }

    public static boolean banIp(MinecraftServer server, UUID targetId, String targetName,
                                String reason, CommandSourceStack source) {
        if (available()) {
            try {
                PunishmentData data = PunishmentData.create(targetId, targetName,
                        Component.literal(targetName), null, sourceOrServer(source, server), reason,
                        0L, PunishmentType.IP_BAN);
                BanHammer.punish(data);
                return true;
            } catch (Throwable throwable) {
                LOGGER.warn("[BrokenStarSMP] BanHammer ip-ban failed for {}; falling back to vanilla", targetName, throwable);
            }
        }
        return vanillaCommand(server, "ban-ip " + targetName + " " + quote(reason));
    }

    public static boolean mute(MinecraftServer server, UUID targetId, String targetName,
                               long durationTicks, String reason, CommandSourceStack source) {
        if (!available()) {
            return false;
        }
        try {
            PunishmentData data = PunishmentData.create(targetId, targetName,
                    Component.literal(targetName), null, sourceOrServer(source, server), reason,
                    durationTicks * 50L, PunishmentType.MUTE);
            BanHammer.punish(data);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("[BrokenStarSMP] BanHammer mute failed for {}", targetName, throwable);
            return false;
        }
    }

    public static boolean kick(MinecraftServer server, UUID targetId, String targetName,
                               String reason, CommandSourceStack source) {
        if (available()) {
            try {
                PunishmentData data = PunishmentData.create(targetId, targetName,
                        Component.literal(targetName), null, sourceOrServer(source, server), reason,
                        0L, PunishmentType.KICK);
                BanHammer.punish(data);
                return true;
            } catch (Throwable throwable) {
                LOGGER.warn("[BrokenStarSMP] BanHammer kick failed for {}; falling back to vanilla", targetName, throwable);
            }
        }
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player != null) {
            player.connection.disconnect(Component.literal(reason == null ? "Kicked" : reason));
            return true;
        }
        return false;
    }

    public static boolean unban(MinecraftServer server, UUID targetId) {
        if (available()) {
            try {
                int removed = BanHammer.removePunishment(targetId, PunishmentType.BAN);
                if (removed > 0) {
                    return true;
                }
            } catch (Throwable throwable) {
                LOGGER.warn("[BrokenStarSMP] BanHammer unban failed for {}", targetId, throwable);
            }
        }
        return vanillaCommand(server, "pardon " + nameOf(server, targetId));
    }

    public static boolean isBanned(UUID targetId) {
        if (!available()) {
            return false;
        }
        try {
            return BanHammer.isPunished(targetId, PunishmentType.BAN);
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static Map<String, String> activePunishments(UUID targetId) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!available()) {
            return out;
        }
        try {
            Collection<PunishmentData.Synced> punishments = BanHammer.getPunishments(targetId);
            for (PunishmentData punishment : punishments) {
                if (punishment.isExpired()) {
                    continue;
                }
                out.put(punishment.type.databaseName,
                        punishment.isTemporary() ? punishment.getFormattedExpirationTime() : "permanent");
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void registerAuditBridge(com.palordersoftworks.brokenstarsmpmod.framework.BrokenStarsFramework framework) {
        if (!available() || framework == null) {
            return;
        }
        try {
            BanHammer.registerPunishmentEvent((data, silent, immediate) ->
                    framework.recordAudit(null, BanHammerAdmins.resolve(data),
                            "punishment." + data.type.databaseName,
                            data.playerName, "", data.reason == null ? "" : data.reason, "banhammer"));
        } catch (Throwable throwable) {
            LOGGER.warn("[BrokenStarSMP] Failed to register BanHammer punishment event", throwable);
        }
    }

    private static boolean vanillaBan(MinecraftServer server, UUID targetId, String reason) {
        String name = nameOf(server, targetId);
        if (name == null) {
            return false;
        }
        return vanillaCommand(server, "ban " + name + " " + quote(reason));
    }

    private static boolean vanillaTempBan(MinecraftServer server, UUID targetId, String reason, long durationTicks) {
        long seconds = Math.max(1L, durationTicks / 20L);
        String name = nameOf(server, targetId);
        if (name == null) {
            return false;
        }
        return vanillaCommand(server, "tempban " + name + " " + seconds + "s " + quote(reason));
    }

    private static boolean vanillaCommand(MinecraftServer server, String command) {
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("[BrokenStarSMP] Vanilla punishment command failed: {}", command, throwable);
            return false;
        }
    }

    private static String nameOf(MinecraftServer server, UUID targetId) {
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player != null) {
            return player.getName().getString();
        }
        try {
            net.minecraft.server.players.NameAndId nameAndId = server.services().nameToIdCache().get(targetId)
                    .orElse(null);
            return nameAndId == null ? null : nameAndId.name();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String quote(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return "\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static CommandSourceStack sourceOrServer(CommandSourceStack source, MinecraftServer server) {
        return source != null ? source : server.createCommandSourceStack();
    }

    private static final class BanHammerAdmins {
        static String resolve(PunishmentData data) {
            try {
                Component admin = data.adminDisplayName;
                return admin == null ? "server" : admin.getString();
            } catch (Throwable ignored) {
                return "server";
            }
        }
    }
}
