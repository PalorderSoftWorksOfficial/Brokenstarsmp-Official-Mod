package com.palordersoftworks.brokenstarsmpmod.framework;

import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.framework.audit.AuditLog;
import com.palordersoftworks.brokenstarsmpmod.framework.config.FrameworkConfigService;
import com.palordersoftworks.brokenstarsmpmod.framework.config.ReloadResult;
import com.palordersoftworks.brokenstarsmpmod.framework.emergency.EmergencyService;
import com.palordersoftworks.brokenstarsmpmod.framework.events.EventDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.events.EventService;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicRegistry;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolution;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolver;
import com.palordersoftworks.brokenstarsmpmod.framework.permissions.FrameworkPermissionService;
import com.palordersoftworks.brokenstarsmpmod.framework.performance.PerformanceManager;
import com.palordersoftworks.brokenstarsmpmod.framework.profiles.ProfileService;
import com.palordersoftworks.luaj.accesswidener.LuaScriptManager;
import com.palordersoftworks.brokenstarsmpmod.messages.Messages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class BrokenStarsFramework {
    private static volatile BrokenStarsFramework instance;

    private final MechanicRegistry registry = new MechanicRegistry();
    private final MechanicResolver resolver = new MechanicResolver(registry);
    private final FrameworkConfigService config = new FrameworkConfigService(registry, resolver);
    private final ProfileService profiles = new ProfileService(config, registry, resolver);
    private final EventService events = new EventService(config, registry, resolver);
    private final EmergencyService emergency = new EmergencyService(resolver);
    private final PerformanceManager performance = new PerformanceManager();
    private final Map<String, Predicate<MechanicContext>> conditions = new LinkedHashMap<>();
    private final Map<String, List<Consumer<String>>> callbacks = new ConcurrentHashMap<>();
    private volatile AuditLog audit;
    private volatile MinecraftServer server;
    private volatile String activeProfile = "default";

    public BrokenStarsFramework() {
        instance = this;
        config.reload();
        profiles.reload();
        events.reload();
        activeProfile = profiles.active();
    }

    public static BrokenStarsFramework get() {
        BrokenStarsFramework current = instance;
        if (current == null) {
            throw new IllegalStateException("BrokenStars framework is not initialized");
        }
        return current;
    }

    public static BrokenStarsFramework getOrNull() {
        return instance;
    }

    public void bind(MinecraftServer server) {
        this.server = server;
        this.events.bind(server);
        this.audit = new AuditLog(config.directory().resolve("data"), config.auditRetention());
    }

    public void shutdown() {
        events.stopAll();
        emergency.clear();
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.reload(config.auditRetention());
        }
        if (server != null) {
            instance = null;
            server = null;
        }
    }

    public void tick(MinecraftServer server) {
        events.tick(server);
        emergency.tick(server);
        performance.tick(server);
        performance.sampleTickTime(server);
        if (server.getTickCount() % 20 == 0) {
            dispatchCallback("tick", String.valueOf(server.getTickCount()));
        }
    }

    public synchronized ReloadResult reload(String section, UUID actor, String actorName) {
        ConfigManager.reloadAll();
        ReloadResult result = config.reload(section);
        if (section == null || section.equalsIgnoreCase("all") || section.equalsIgnoreCase("profiles")
                || section.equalsIgnoreCase("profile")) {
            profiles.reload();
            activeProfile = profiles.active();
        }
        if (section == null || section.equalsIgnoreCase("all") || section.equalsIgnoreCase("events")
                || section.equalsIgnoreCase("event")) {
            events.reload();
        }
        if (section == null || section.equalsIgnoreCase("all") || section.equalsIgnoreCase("messages")) {
            try {
                Messages.reload();
            } catch (Exception exception) {
                result = new ReloadResult(false, result.loadedFiles(), result.loadedRules(), result.issues(),
                        result.restartRequired(), result.activeProfile(), result.lastReload(), result.configVersion());
            }
        }
        if (section == null || section.equalsIgnoreCase("all") || section.equalsIgnoreCase("scripts")) {
            LuaScriptManager.reloadShared();
        }
        if (audit != null) {
            audit.reload(config.auditRetention());
        }
        recordAudit(actor, actorName, "configuration.reload", section == null ? "all" : section,
                result.activeProfile(), result.activeProfile(), "command");
        return result;
    }

    public MechanicRegistry registry() {
        return registry;
    }

    public MechanicResolver resolver() {
        return resolver;
    }

    public FrameworkConfigService config() {
        return config;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public EventService events() {
        return events;
    }

    public EmergencyService emergency() {
        return emergency;
    }

    public PerformanceManager performance() {
        return performance;
    }

    public MechanicResolution read(String mechanicId, MechanicContext context) {
        return resolver.resolve(mechanicId, context);
    }

    public MechanicResolution write(String mechanicId, Object value, MechanicContext context,
                                   UUID actor, String actorName) {
        MechanicResolution before = resolver.resolve(mechanicId, context);
        resolver.write(mechanicId, value, context, "api");
        MechanicResolution after = resolver.resolve(mechanicId, context);
        recordAudit(actor, actorName, "mechanic.write", mechanicId, before.value(), after.value(), "api");
        return after;
    }

    public boolean remove(String overrideId, UUID actor, String actorName) {
        boolean removed = resolver.remove(overrideId);
        if (removed) {
            recordAudit(actor, actorName, "mechanic.remove", overrideId, "active", "removed", "api");
        }
        return removed;
    }

    public void registerMechanic(MechanicDefinition definition) {
        registry.register(definition);
    }

    public void registerCondition(String id, Predicate<MechanicContext> condition) {
        if (id != null && condition != null) {
            conditions.put(id, condition);
        }
    }

    public boolean evaluateCondition(String id, MechanicContext context) {
        Predicate<MechanicContext> condition = conditions.get(id);
        return condition != null && condition.test(context == null ? MechanicContext.global() : context);
    }

    public void registerEventCallback(String id, Consumer<EventDefinition> callback) {
        events.registerCallback(id, callback);
    }

    public void registerCallback(String id, Consumer<String> callback) {
        if (id == null || callback == null) {
            return;
        }
        callbacks.computeIfAbsent(id, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(callback);
    }

    public void dispatchCallback(String id, String payload) {
        for (Consumer<String> callback : callbacks.getOrDefault(id, List.of())) {
            try {
                callback.accept(payload == null ? "" : payload);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public MechanicContext context(ServerPlayer player) {
        if (player == null) {
            return MechanicContext.global();
        }
        return FrameworkPermissionService.enrich(MechanicContext.builder()
                .dimension(player.level().dimension().identifier().toString())
                .player(player.getUUID())
                .build(), player);
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("config_version=" + config.configVersion());
        lines.add("last_reload=" + config.lastReload());
        lines.add("loaded_files=" + String.join(",", config.loadedFiles()));
        lines.add("loaded_rules=" + config.loadedRuleCount());
        lines.add("invalid_rules=" + config.issues().stream().filter(issue -> issue.severity() == com.palordersoftworks.brokenstarsmpmod.framework.config.ConfigIssue.Severity.ERROR).count());
        lines.add("active_profile=" + activeProfile);
        lines.add("restart_required=" + (config.restartRequired().isEmpty() ? "none" : String.join(",", config.restartRequired())));
        lines.add("active_events=" + events.active().stream().map(EventDefinition::id).toList());
        lines.add("emergency=" + (server == null ? "normal" : emergency.status(server)));
        lines.add("luckperms=" + (com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat.luckPermsAvailable()
                ? "connected" : "absent"));
        lines.add("placeholders=" + (com.palordersoftworks.brokenstarsmpmod.framework.placeholders.BrokenStarsPlaceholders
                .isRegistered() ? "registered" : "unavailable"));
        lines.add("banhammer=" + (com.palordersoftworks.brokenstarsmpmod.framework.punishments.PunishmentService
                .available() ? "connected" : "vanilla-fallback"));
        return lines;
    }

    public void recordAudit(UUID actor, String actorName, String action, String target,
                            Object previous, Object next, String source) {
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.record(actor, actorName, action, target, previous, next, source, activeProfile,
                    events.active().stream().map(EventDefinition::id).findFirst().orElse(""));
        }
    }
}
