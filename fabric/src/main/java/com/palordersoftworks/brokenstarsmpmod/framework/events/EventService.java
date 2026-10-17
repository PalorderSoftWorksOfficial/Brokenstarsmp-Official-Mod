package com.palordersoftworks.brokenstarsmpmod.framework.events;

import com.palordersoftworks.brokenstarsmpmod.framework.config.ConfigIssue;
import com.palordersoftworks.brokenstarsmpmod.framework.config.FrameworkConfigService;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicOverride;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicRegistry;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolver;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicValueType;
import com.palordersoftworks.brokenstarsmpmod.messages.MiniMessageApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventService {
    private final FrameworkConfigService config;
    private final MechanicRegistry registry;
    private final MechanicResolver resolver;
    private final Map<String, EventDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ActiveEvent> active = new LinkedHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<EventDefinition>>> callbacks = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;

    public EventService(FrameworkConfigService config, MechanicRegistry registry, MechanicResolver resolver) {
        this.config = config;
        this.registry = registry;
        this.resolver = resolver;
    }

    public void bind(MinecraftServer server) {
        this.server = server;
    }

    public synchronized void reload() {
        for (String id : List.copyOf(active.keySet())) {
            stop(id);
        }
        definitions.clear();
        Map<String, Object> document = config.document(FrameworkConfigService.EVENTS_FILE);
        Object rawEvents = document.get("events");
        if (rawEvents instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                    config.report(ConfigIssue.error(FrameworkConfigService.EVENTS_FILE, "events",
                            String.valueOf(entry.getKey()), entry.getValue(), "event mapping", "ignored", "event must be a mapping"));
                    continue;
                }
                try {
                    definitions.put(id, parse(id, stringMap(raw)));
                } catch (RuntimeException exception) {
                    config.report(ConfigIssue.error(FrameworkConfigService.EVENTS_FILE, "events", id, raw,
                            "valid event", "ignored", exception.getMessage()));
                }
            }
        }
    }

    public synchronized List<EventDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public synchronized List<EventDefinition> active() {
        return List.copyOf(active.values().stream().map(ActiveEvent::definition).toList());
    }

    public synchronized EventDefinition get(String id) {
        return definitions.get(id);
    }

    public synchronized StartResult start(String id) {
        EventDefinition definition = definitions.get(id);
        if (definition == null) {
            return StartResult.failure("unknown event");
        }
        MinecraftServer activeServer = server;
        if (activeServer == null) {
            return StartResult.failure("server is not ready");
        }
        if (active.containsKey(id)) {
            return StartResult.failure("event is already active");
        }
        long now = activeServer.getTickCount();
        long readyAt = cooldowns.getOrDefault(id, -1L);
        if (readyAt > now) {
            return StartResult.failure("event cooldown has " + (readyAt - now) + " ticks remaining");
        }
        if (!conditionsPass(definition.startConditions(), activeServer)) {
            return StartResult.failure("start conditions are not met");
        }
        long duration = definition.durationTicks() <= 0 ? -1L : now + definition.durationTicks();
        List<MechanicOverride> overrides = new ArrayList<>();
        for (Map.Entry<String, Object> entry : definition.overrides().entrySet()) {
            MechanicDefinition mechanic = registry.get(entry.getKey());
            if (mechanic == null) {
                config.report(ConfigIssue.error(FrameworkConfigService.EVENTS_FILE, "events." + id + ".overrides",
                        entry.getKey(), entry.getValue(), "registered mechanic", "ignored", "event override is not registered"));
                continue;
            }
            try {
                overrides.add(new MechanicOverride("event:" + id + ":" + entry.getKey(), entry.getKey(),
                        mechanic.validate(entry.getValue()), com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext.global(),
                        "event:" + id, 200 + Math.max(0, definition.priority()), -1, mechanic.restartRequired()));
            } catch (RuntimeException exception) {
                config.report(ConfigIssue.error(FrameworkConfigService.EVENTS_FILE, "events." + id + ".overrides",
                        entry.getKey(), entry.getValue(), mechanic.type().serializedName(), String.valueOf(mechanic.defaultValue()), exception.getMessage()));
            }
        }
        for (MechanicOverride override : overrides) {
            resolver.add(override);
        }
        active.put(id, new ActiveEvent(definition, duration, now));
        if (definition.cooldownTicks() > 0) {
            cooldowns.put(id, now + definition.cooldownTicks());
        }
        dispatch(id, definition, true);
        return StartResult.success(definition);
    }

    public synchronized boolean stop(String id) {
        ActiveEvent current = active.remove(id);
        if (current == null) {
            return false;
        }
        resolver.removeSource("event:" + id);
        dispatch(id, current.definition(), false);
        return true;
    }

    public synchronized void tick(MinecraftServer activeServer) {
        long now = activeServer.getTickCount();
        for (Map.Entry<String, ActiveEvent> entry : List.copyOf(active.entrySet())) {
            ActiveEvent value = entry.getValue();
            if (value.expiresAtTick() >= 0 && now >= value.expiresAtTick()) {
                stop(entry.getKey());
            }
        }
    }

    public void registerCallback(String id, Consumer<EventDefinition> callback) {
        if (id == null || callback == null) {
            return;
        }
        callbacks.computeIfAbsent(id, ignored -> new ArrayList<>()).add(callback);
    }

    public void stopAll() {
        for (String id : List.copyOf(active.keySet())) {
            stop(id);
        }
    }

    private void dispatch(String id, EventDefinition definition, boolean started) {
        String action = started ? "event.start" : "event.stop";
        List<Consumer<EventDefinition>> registered = callbacks.getOrDefault(id, List.of());
        for (Consumer<EventDefinition> callback : registered) {
            try {
                callback.accept(definition);
            } catch (RuntimeException ignored) {
            }
        }
        for (String callbackName : definition.luaCallbacks()) {
            List<Consumer<EventDefinition>> named = callbacks.getOrDefault(callbackName, List.of());
            for (Consumer<EventDefinition> callback : named) {
                try {
                    callback.accept(definition);
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (!started) {
            return;
        }
        MinecraftServer activeServer = server;
        if (activeServer == null) {
            return;
        }
        for (String message : definition.messages()) {
            broadcast(activeServer, message);
        }
        for (String command : definition.commands()) {
            if (command != null && !command.isBlank()) {
                activeServer.execute(() -> activeServer.getCommands().performPrefixedCommand(
                        activeServer.createCommandSourceStack().withSuppressedOutput(), command));
            }
        }
    }

    private void broadcast(MinecraftServer activeServer, String message) {
        Component component = MiniMessageApi.toNative(MiniMessageApi.parse(message));
        for (ServerPlayer player : activeServer.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    private boolean conditionsPass(Map<String, Object> conditions, MinecraftServer activeServer) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            if (entry.getKey().equals("players_at_least") && entry.getValue() instanceof Number number
                    && activeServer.getPlayerCount() < number.intValue()) {
                return false;
            }
            if (entry.getKey().equals("manual_only") && Boolean.TRUE.equals(entry.getValue())) {
                return true;
            }
        }
        return true;
    }

    private EventDefinition parse(String id, Map<String, Object> values) {
        if (!id.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid event id");
        }
        long duration = duration(values.get("duration"), 12000L);
        long cooldown = duration(values.get("cooldown"), 0L);
        int priority = values.get("priority") instanceof Number number ? number.intValue() : 0;
        Map<String, Object> overrides = map(values.get("overrides"));
        List<String> messages = strings(values.get("messages"));
        List<String> commands = strings(values.get("commands"));
        List<String> luaCallbacks = strings(values.get("lua_callbacks"));
        return new EventDefinition(id, !Boolean.FALSE.equals(values.get("enabled")), duration, cooldown, priority,
                overrides, messages, commands, luaCallbacks, map(values.get("start_conditions")),
                map(values.get("end_conditions")));
    }

    private long duration(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        return ((Number) MechanicValueType.DURATION.parse(value)).longValue();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> stringMap(Map<?, ?> values) {
        return map(values);
    }

    public record StartResult(boolean successful, String message, EventDefinition definition) {
        static StartResult success(EventDefinition definition) {
            return new StartResult(true, "started", definition);
        }

        static StartResult failure(String message) {
            return new StartResult(false, message, null);
        }
    }

    public record ActiveEvent(EventDefinition definition, long expiresAtTick, long startedAtTick) {
    }
}
