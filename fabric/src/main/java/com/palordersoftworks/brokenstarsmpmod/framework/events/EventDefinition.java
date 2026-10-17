package com.palordersoftworks.brokenstarsmpmod.framework.events;

import java.util.List;
import java.util.Map;

public record EventDefinition(
        String id,
        boolean enabled,
        long durationTicks,
        long cooldownTicks,
        int priority,
        Map<String, Object> overrides,
        List<String> messages,
        List<String> commands,
        List<String> luaCallbacks,
        Map<String, Object> startConditions,
        Map<String, Object> endConditions
) {
    public EventDefinition {
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        messages = messages == null ? List.of() : List.copyOf(messages);
        commands = commands == null ? List.of() : List.copyOf(commands);
        luaCallbacks = luaCallbacks == null ? List.of() : List.copyOf(luaCallbacks);
        startConditions = startConditions == null ? Map.of() : Map.copyOf(startConditions);
        endConditions = endConditions == null ? Map.of() : Map.copyOf(endConditions);
    }
}
