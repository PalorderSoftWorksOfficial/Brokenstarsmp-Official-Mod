package com.palordersoftworks.brokenstarsmpmod.framework.permissions;

import com.palordersoftworks.brokenstarsmpmod.framework.BrokenStarsFramework;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public final class FrameworkPermissionService {
    private static final String CONTEXT_TEAM = "brokenstarsmp:team";
    private static final String CONTEXT_PROFILE = "brokenstarsmp:profile";
    private static final String CONTEXT_EVENT = "brokenstarsmp:event";
    private static final String CONTEXT_GROUP = "brokenstarsmp:group";
    private static final String CONTEXT_DIMENSION = "brokenstarsmp:dimension";

    private FrameworkPermissionService() {
    }

    public static void init() {
        LpHook.registerContextCalculator("team", CONTEXT_TEAM, player -> {
            TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
            return team == null ? null : sanitize(team.name());
        });
        LpHook.registerContextCalculator("profile", CONTEXT_PROFILE,
                player -> BrokenStarsFramework.getOrNull() == null
                        ? null
                        : BrokenStarsFramework.getOrNull().profiles().active());
        LpHook.registerContextCalculator("event", CONTEXT_EVENT, player -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return null;
            }
            List<com.palordersoftworks.brokenstarsmpmod.framework.events.EventDefinition> active =
                    framework.events().active();
            return active.isEmpty() ? null : active.get(0).id();
        });
        LpHook.registerContextCalculator("group", CONTEXT_GROUP,
                player -> LpHook.primaryGroup(player.getUUID()).orElse(null));
        LpHook.registerContextCalculator("dimension", CONTEXT_DIMENSION,
                player -> String.valueOf(player.level().dimension().identifier()));
    }

    public static void shutdown() {
        LpHook.unregisterAll();
    }

    public static MechanicContext enrich(MechanicContext base, ServerPlayer player) {
        if (player == null) {
            return base;
        }
        MechanicContext.Builder builder = base.toBuilder();
        if (base.rank() == null) {
            LpHook.primaryGroup(player.getUUID()).ifPresent(builder::rank);
        }
        if (base.team() == null) {
            TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
            if (team != null) {
                builder.team(sanitize(team.name()));
            }
        }
        if (base.dimension() == null) {
            builder.dimension(String.valueOf(player.level().dimension().identifier()));
        }
        return builder.build();
    }

    public static boolean checkNode(UUID playerId, String node) {
        return LpHook.check(playerId, node);
    }

    public static Function<ServerPlayer, String> groupResolver() {
        return player -> LpHook.primaryGroup(player.getUUID()).orElse(null);
    }

    private static String sanitize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
