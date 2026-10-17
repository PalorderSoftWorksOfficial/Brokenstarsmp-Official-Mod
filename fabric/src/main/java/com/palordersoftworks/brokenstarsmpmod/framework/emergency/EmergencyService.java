package com.palordersoftworks.brokenstarsmpmod.framework.emergency;

import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicOverride;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolver;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EmergencyService {
    public enum Toggle {
        EXPLOSIONS("explosions_enabled"),
        MOBS("mob_spawning_enabled"),
        REDSTONE("block_redstone_behavior_enabled"),
        DROPS("loot_drops_enabled"),
        MECHANICS("safe_mode");

        private final String mechanicId;

        Toggle(String mechanicId) {
            this.mechanicId = mechanicId;
        }

        public String mechanicId() {
            return mechanicId;
        }
    }

    private final MechanicResolver resolver;
    private final Map<Toggle, EmergencyState> states = new LinkedHashMap<>();

    public EmergencyService(MechanicResolver resolver) {
        this.resolver = resolver;
    }

    public synchronized boolean set(Toggle toggle, boolean enabled, long durationTicks, MinecraftServer server) {
        if (toggle == null) {
            return false;
        }
        if (!enabled) {
            states.remove(toggle);
            resolver.removeSource(source(toggle));
            return true;
        }
        long expires = durationTicks <= 0 ? -1L : server.getTickCount() + durationTicks;
        states.put(toggle, new EmergencyState(enabled, expires));
        resolver.add(new MechanicOverride("emergency:" + toggle.name().toLowerCase(java.util.Locale.ROOT),
                toggle.mechanicId(), false, MechanicContext.global(), source(toggle), 1000, -1, false));
        return true;
    }

    public synchronized boolean clear() {
        boolean changed = !states.isEmpty();
        states.clear();
        for (Toggle toggle : Toggle.values()) {
            resolver.removeSource(source(toggle));
        }
        return changed;
    }

    public synchronized void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Map.Entry<Toggle, EmergencyState> entry : List.copyOf(states.entrySet())) {
            if (entry.getValue().expiresAtTick() >= 0 && now >= entry.getValue().expiresAtTick()) {
                states.remove(entry.getKey());
                resolver.removeSource(source(entry.getKey()));
            }
        }
    }

    public synchronized Map<Toggle, EmergencyState> states() {
        return Map.copyOf(states);
    }

    public synchronized List<String> status(MinecraftServer server) {
        if (states.isEmpty()) {
            return List.of("normal");
        }
        return states.entrySet().stream().map(entry -> {
            EmergencyState state = entry.getValue();
            return entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                    + (state.expiresAtTick() < 0 ? "=on" : "=on:" + Math.max(0, state.expiresAtTick() - server.getTickCount()) + "t");
        }).toList();
    }

    private String source(Toggle toggle) {
        return "emergency:" + toggle.name().toLowerCase(java.util.Locale.ROOT);
    }

    public record EmergencyState(boolean enabled, long expiresAtTick) {
    }
}
