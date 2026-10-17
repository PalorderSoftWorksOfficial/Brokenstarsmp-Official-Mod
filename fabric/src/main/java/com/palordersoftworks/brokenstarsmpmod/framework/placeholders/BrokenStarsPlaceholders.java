package com.palordersoftworks.brokenstarsmpmod.framework.placeholders;

import com.palordersoftworks.brokenstarsmpmod.framework.BrokenStarsFramework;
import com.palordersoftworks.brokenstarsmpmod.framework.emergency.EmergencyService;
import com.palordersoftworks.brokenstarsmpmod.framework.events.EventDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolution;
import com.palordersoftworks.brokenstarsmpmod.framework.performance.PerformanceManager;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

public final class BrokenStarsPlaceholders {
    private static final String NAMESPACE = "brokenstarsmp";
    private static volatile boolean registered;

    private BrokenStarsPlaceholders() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        try {
            registerCommon();
            registerServer();
            registered = true;
        } catch (Throwable throwable) {
            registered = false;
        }
    }

    public static boolean isRegistered() {
        return registered;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    private static void registerCommon() {
        Placeholders.registerCommon(id("profile"), (ctx, arg) -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return PlaceholderResult.invalid("no framework");
            }
            return PlaceholderResult.value(framework.profiles().active());
        });

        Placeholders.registerCommon(id("events"), (ctx, arg) -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return PlaceholderResult.invalid("no framework");
            }
            List<EventDefinition> active = framework.events().active();
            return PlaceholderResult.value(active.isEmpty() ? "none"
                    : String.join(", ", active.stream().map(EventDefinition::id).toList()));
        });

        Placeholders.registerCommon(id("event_count"), (ctx, arg) -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            return framework == null ? PlaceholderResult.invalid("no framework")
                    : PlaceholderResult.value(String.valueOf(framework.events().active().size()));
        });

        Placeholders.registerCommon(id("mechanic_<mechanic_id>"), (ctx, arg) -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null || arg == null || arg.isBlank()) {
                return PlaceholderResult.invalid("missing mechanic id");
            }
            MechanicResolution resolution = framework.resolver().resolve(arg.trim(), MechanicContext.global());
            return PlaceholderResult.value(String.valueOf(resolution.value()));
        });

        Placeholders.registerCommon(id("team"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !(ctx.player() instanceof ServerPlayer player)) {
                return PlaceholderResult.invalid("player only");
            }
            TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
            return PlaceholderResult.value(team == null ? "none" : team.name());
        });

        Placeholders.registerCommon(id("team_members"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !(ctx.player() instanceof ServerPlayer player)) {
                return PlaceholderResult.invalid("player only");
            }
            TeamManager.TeamData team = TeamManager.getTeamOf(player.getUUID());
            return PlaceholderResult.value(team == null ? "0" : String.valueOf(team.members().size()));
        });

        Placeholders.registerCommon(id("balance"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !(ctx.player() instanceof ServerPlayer player)) {
                return PlaceholderResult.invalid("player only");
            }
            try {
                EconomyManager manager = EconomyCraft.getManager(player.level().getServer());
                if (manager == null) {
                    return PlaceholderResult.value("0");
                }
                return PlaceholderResult.value(EconomyCraft.formatMoney(manager.getBalance(player.getUUID(), true)));
            } catch (Throwable throwable) {
                return PlaceholderResult.invalid("economy unavailable");
            }
        });
    }

    private static void registerServer() {
        Placeholders.registerServer(id("mspt"), (ctx, arg) -> {
            PerformanceManager performance = performance();
            return PlaceholderResult.value(performance == null ? "n/a"
                    : String.format(Locale.ROOT, "%.2f", performance.averageTickMillis()));
        });

        Placeholders.registerServer(id("tps"), (ctx, arg) -> {
            PerformanceManager performance = performance();
            return PlaceholderResult.value(performance == null ? "n/a"
                    : String.format(Locale.ROOT, "%.1f", performance.estimatedTps()));
        });

        Placeholders.registerServer(id("entities"), (ctx, arg) -> {
            PerformanceManager performance = performance();
            return PlaceholderResult.value(performance == null ? "n/a"
                    : String.valueOf(performance.snapshot().entities()));
        });

        Placeholders.registerServer(id("mobs"), (ctx, arg) -> {
            PerformanceManager performance = performance();
            return PlaceholderResult.value(performance == null ? "n/a"
                    : String.valueOf(performance.snapshot().mobs()));
        });

        Placeholders.registerServer(id("players"), (ctx, arg) -> {
            PerformanceManager performance = performance();
            return PlaceholderResult.value(performance == null ? "n/a"
                    : String.valueOf(performance.snapshot().players()));
        });

        Placeholders.registerServer(id("emergency"), (ctx, arg) -> {
            BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
            if (framework == null) {
                return PlaceholderResult.invalid("no framework");
            }
            EmergencyService service = framework.emergency();
            return PlaceholderResult.value(service.states().isEmpty() ? "normal" : "active");
        });
    }

    private static PerformanceManager performance() {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework == null ? null : framework.performance();
    }
}
