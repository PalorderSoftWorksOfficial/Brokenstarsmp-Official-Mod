package com.palordersoftworks.brokenstarsmpmod.teams;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.palordersoftworks.brokenstarsmpmod.config.TeamRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String OWNER_TAG = "bs_team_owner";
    public static final String MEMBER_TAG_PREFIX = "bs_team_";

    private static final Map<String, TeamData> TEAMS = new HashMap<>();
    private static final Map<UUID, Set<String>> INVITES = new HashMap<>();
    private static MinecraftServer server;

    private TeamManager() {
    }

    public static void init(MinecraftServer activeServer) {
        server = activeServer;
        TEAMS.clear();
        INVITES.clear();
        load();
    }

    public static void shutdown() {
        save();
        server = null;
    }

    public static synchronized void save() {
        if (server == null) {
            return;
        }
        try {
            Map<String, Map<String, Object>> serialized = new HashMap<>();
            for (Map.Entry<String, TeamData> entry : TEAMS.entrySet()) {
                TeamData data = entry.getValue();
                Map<String, Object> blob = new HashMap<>();
                blob.put("owner", data.owner.toString());
                blob.put("members", data.members.stream().map(UUID::toString).toList());
                blob.put("color", data.color);
                serialized.put(entry.getKey(), blob);
            }
            Path file = dataFile();
            if (file != null) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(serialized), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error("[BrokenStarSMP] Failed to save teams", e);
        }
    }

    private static void load() {
        Path file = dataFile();
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Map<String, Object>> serialized =
                    GSON.fromJson(json, new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            if (serialized == null) {
                return;
            }
            for (Map.Entry<String, Map<String, Object>> entry : serialized.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> blob = entry.getValue();
                UUID owner;
                try {
                    owner = UUID.fromString(String.valueOf(blob.get("owner")));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Set<UUID> members = new HashSet<>();
                if (blob.get("members") instanceof List<?> rawMembers) {
                    for (Object rawMember : rawMembers) {
                        try {
                            members.add(UUID.fromString(String.valueOf(rawMember)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                members.add(owner);
                String color = String.valueOf(blob.getOrDefault("color", "reset"));
                TEAMS.put(name, new TeamData(name, owner, members, color));
                TeamManager.ensureVanillaTeam(name, color);
            }
            LOGGER.info("[BrokenStarSMP] Loaded {} team(s)", TEAMS.size());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[BrokenStarSMP] Failed to load teams", e);
        }
    }

    private static Path dataFile() {
        if (server == null) {
            return null;
        }
        return server.getServerDirectory().resolve("config").resolve("brokenstarsmp")
                .resolve("data").resolve("teams.json");
    }

    public record TeamData(String name, UUID owner, Set<UUID> members, String color) {
    }

    private static String safeColor(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("reset")) {
            return "reset";
        }
        try {
            return TeamColor.valueOf(normalized.toUpperCase(Locale.ROOT)).getSerializedName();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean vanillaTeamExists(String name) {
        return server != null && server.getScoreboard().getPlayerTeam(name) != null;
    }

    private static void ensureVanillaTeam(String name, String color) {
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        String resolvedColor = safeColor(color == null ? "reset" : color);
        if ("reset".equals(resolvedColor)) {
            team.setColor(java.util.Optional.empty());
        } else {
            team.setColor(java.util.Optional.of(TeamColor.valueOf(resolvedColor.toUpperCase(Locale.ROOT))));
        }
        team.setDisplayName(Component.literal(name));
        team.setAllowFriendlyFire(false);
        // setColor already triggers a modify broadcast; this add-packet forces full state sync.
        server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    private static void destroyVanillaTeam(String name) {
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team != null) {
            scoreboard.removePlayerTeam(team); // ServerScoreboard broadcasts the removal itself
        }
    }

    private static void movePlayerVanillaTeam(ServerPlayer player, boolean joining, String teamName) {
        if (server == null) {
            return;
        }
        // ServerScoreboard broadcasts the join/leave packets to clients itself.
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            return;
        }
        String scoreboardName = player.getScoreboardName();
        if (joining) {
            scoreboard.addPlayerToTeam(scoreboardName, team);
        } else if (player.getTeam() == team) {
            scoreboard.removePlayerFromTeam(scoreboardName);
        }
    }

    private static void syncPlayerTeamMembership(ServerPlayer player) {
        TeamData team = getTeamOf(player.getUUID());
        if (team != null && vanillaTeamExists(team.name)) {
            movePlayerVanillaTeam(player, true, team.name);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        syncPlayerTeamMembership(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        TeamData team = getTeamOf(player.getUUID());
        if (team != null) {
            movePlayerVanillaTeam(player, false, team.name);
        }
    }

    public static synchronized TeamData getTeamOf(UUID member) {
        for (TeamData data : TEAMS.values()) {
            if (data.members.contains(member)) {
                return data;
            }
        }
        return null;
    }

    public static synchronized TeamData getTeam(String name) {
        return TEAMS.get(name);
    }

    public static synchronized List<TeamData> allTeams() {
        return new ArrayList<>(TEAMS.values());
    }

    public static synchronized boolean teamExists(String name) {
        return TEAMS.containsKey(name);
    }

    public static synchronized String createTeam(String name, UUID owner) {
        if (TEAMS.containsKey(name)) {
            return "A team with that name already exists.";
        }
        if (getTeamOf(owner) != null) {
            return "You are already on a team. Leave it first with /teams leave.";
        }
        TEAMS.put(name, new TeamData(name, owner, new HashSet<>(Set.of(owner)), "reset"));
        ensureVanillaTeam(name, "reset");
        applyOwnerTag(owner, name);
        save();
        return null;
    }

    public static synchronized String deleteTeam(String name, UUID actor) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (!team.owner.equals(actor)) {
            return "Only the team owner can delete the team.";
        }
        for (UUID member : team.members) {
            clearTags(member, name);
        }
        destroyVanillaTeam(name);
        TEAMS.remove(name);
        save();
        return null;
    }

    public static synchronized String invite(String name, UUID target, UUID actor) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (!team.owner.equals(actor)) {
            return "Only the team owner can invite players.";
        }
        if (getTeamOf(target) != null) {
            return "That player is already on a team.";
        }
        int max = TeamRules.TEAM_MAX_MEMBERS;
        if (max > 0 && team.members.size() >= max) {
            return "Team is full (max " + max + " members).";
        }
        INVITES.computeIfAbsent(target, key -> new HashSet<>()).add(name);
        return null;
    }

    public static synchronized boolean hasInvite(UUID target, String name) {
        Set<String> invites = INVITES.get(target);
        return invites != null && invites.contains(name);
    }

    public static synchronized Set<String> invitesFor(UUID target) {
        return INVITES.getOrDefault(target, Set.of());
    }

    public static synchronized String join(String name, UUID target) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (getTeamOf(target) != null) {
            return "You are already on a team.";
        }
        if (TeamRules.TEAM_REQUIRE_INVITE && !hasInvite(target, name)) {
            return "You don't have an invite to " + name + ".";
        }
        int max = TeamRules.TEAM_MAX_MEMBERS;
        if (max > 0 && team.members.size() >= max) {
            return "Team is full (max " + max + " members).";
        }
        Set<UUID> members = new HashSet<>(team.members);
        members.add(target);
        TEAMS.put(name, new TeamData(name, team.owner, members, team.color));
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) {
            movePlayerVanillaTeam(player, true, name);
        }
        INVITES.computeIfAbsent(target, key -> new HashSet<>()).remove(name);
        save();
        return null;
    }

    public static synchronized String leave(UUID actor) {
        TeamData team = getTeamOf(actor);
        if (team == null) {
            return "You are not on a team.";
        }
        if (team.owner.equals(actor)) {
            return "The owner can't leave; delete the team with /teams disband instead.";
        }
        Set<UUID> members = new HashSet<>(team.members);
        members.remove(actor);
        TEAMS.put(team.name, new TeamData(team.name, team.owner, members, team.color));
        clearTags(actor, team.name);
        save();
        return null;
    }

    public static synchronized String kick(String name, UUID target, UUID actor) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (!team.owner.equals(actor)) {
            return "Only the team owner can kick members.";
        }
        if (team.owner.equals(target)) {
            return "The owner can't be kicked.";
        }
        if (!team.members.contains(target)) {
            return "That player is not a member of " + name + ".";
        }
        Set<UUID> members = new HashSet<>(team.members);
        members.remove(target);
        TEAMS.put(name, new TeamData(name, team.owner, members, team.color));
        clearTags(target, name);
        save();
        return null;
    }

    public static synchronized String transfer(String name, UUID target, UUID actor) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (!team.owner.equals(actor)) {
            return "Only the team owner can transfer ownership.";
        }
        if (!team.members.contains(target) || team.owner.equals(target)) {
            return "Pick a current member who isn't you.";
        }
        clearOwnerTag(team.owner);
        TEAMS.put(name, new TeamData(name, target, new HashSet<>(team.members), team.color));
        applyOwnerTag(target, name);
        save();
        return null;
    }

    public static synchronized String setColor(String name, String colorInput, UUID actor) {
        TeamData team = TEAMS.get(name);
        if (team == null) {
            return "No team named " + name + " exists.";
        }
        if (!team.owner.equals(actor)) {
            return "Only the team owner can change the team color.";
        }
        String color = safeColor(colorInput);
        if (color == null) {
            return "Unknown color " + colorInput + ".";
        }
        TEAMS.put(name, new TeamData(name, team.owner, new HashSet<>(team.members), color));
        ensureVanillaTeam(name, color);
        save();
        return null;
    }

    private static void applyOwnerTag(UUID owner, String teamName) {
        ServerPlayer player = server != null ? server.getPlayerList().getPlayer(owner) : null;
        if (player != null) {
            player.addTag(OWNER_TAG);
        }
    }

    private static void clearOwnerTag(UUID owner) {
        ServerPlayer player = server != null ? server.getPlayerList().getPlayer(owner) : null;
        if (player != null) {
            player.removeTag(OWNER_TAG);
        }
    }

    private static void clearTags(UUID member, String teamName) {
        ServerPlayer player = server != null ? server.getPlayerList().getPlayer(member) : null;
        if (player == null) {
            return;
        }
        player.removeTag(MEMBER_TAG_PREFIX + teamName);
        TeamData remaining = getTeamOf(member);
        if (remaining == null) {
            player.removeTag(OWNER_TAG);
            movePlayerVanillaTeam(player, false, teamName);
        }
    }

    public static synchronized boolean isSameTeam(UUID a, UUID b) {
        if (a.equals(b)) {
            return false;
        }
        TeamData team = getTeamOf(a);
        return team != null && team.members.contains(b);
    }

}
