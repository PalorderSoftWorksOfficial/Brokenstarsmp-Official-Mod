package com.palordersoftworks.brokenstarsmpmod.framework.profiles;

import com.palordersoftworks.brokenstarsmpmod.framework.config.ConfigIssue;
import com.palordersoftworks.brokenstarsmpmod.framework.config.FrameworkConfigService;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicOverride;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicRegistry;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolver;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicScope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileService {
    private final FrameworkConfigService config;
    private final MechanicRegistry registry;
    private final MechanicResolver resolver;
    private final Map<String, MechanicProfile> profiles = new LinkedHashMap<>();
    private volatile String active = "default";

    public ProfileService(FrameworkConfigService config, MechanicRegistry registry, MechanicResolver resolver) {
        this.config = config;
        this.registry = registry;
        this.resolver = resolver;
    }

    public synchronized void reload() {
        profiles.clear();
        Map<String, Object> document = config.document(FrameworkConfigService.PROFILES_FILE);
        Object rawProfiles = document.get("profiles");
        if (rawProfiles instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                    config.report(ConfigIssue.error(FrameworkConfigService.PROFILES_FILE, "profiles",
                            String.valueOf(entry.getKey()), entry.getValue(), "profile mapping", "ignored", "profile must be a mapping"));
                    continue;
                }
                try {
                    profiles.put(id, parse(id, stringMap(raw)));
                } catch (RuntimeException exception) {
                    config.report(ConfigIssue.error(FrameworkConfigService.PROFILES_FILE, "profiles", id, raw,
                            "valid profile", "ignored", exception.getMessage()));
                }
            }
        }
        if (profiles.isEmpty()) {
            profiles.put("default", new MechanicProfile("default", null, Map.of(), "Default profile"));
        }
        String configuredActive = String.valueOf(document.getOrDefault("active", "default"));
        active = profiles.containsKey(configuredActive) ? configuredActive : "default";
        config.setActiveProfile(active);
        applyLayer();
    }

    public synchronized boolean apply(String id) {
        if (!profiles.containsKey(id)) {
            return false;
        }
        active = id;
        config.setActiveProfile(id);
        applyLayer();
        persistActive();
        return true;
    }

    public synchronized boolean create(String id, String parent) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]+")) {
            return false;
        }
        if (profiles.containsKey(id)) {
            return false;
        }
        MechanicProfile profile = new MechanicProfile(id, parent, Map.of(), "Created from commands");
        profiles.put(id, profile);
        persistAll();
        return true;
    }

    public synchronized void remove(String id) {
        if ("default".equals(id)) {
            return;
        }
        profiles.remove(id);
        for (MechanicProfile profile : List.copyOf(profiles.values())) {
            if (id.equals(profile.extendsProfile())) {
                profiles.put(profile.id(), new MechanicProfile(profile.id(), "default", profile.overrides(), profile.description()));
            }
        }
        if (id.equals(active)) {
            active = "default";
        }
        applyLayer();
        persistAll();
    }

    public String active() {
        return active;
    }

    public List<MechanicProfile> all() {
        return List.copyOf(profiles.values());
    }

    public MechanicProfile get(String id) {
        return profiles.get(id);
    }

    private void applyLayer() {
        List<MechanicOverride> overrides = new ArrayList<>();
        MechanicProfile profile = profiles.get(active);
        if (profile != null) {
            for (Map.Entry<String, Object> entry : flatten(active, new HashSet<>()).entrySet()) {
                MechanicDefinition definition = registry.get(entry.getKey());
                if (definition == null) {
                    continue;
                }
                try {
                    overrides.add(new MechanicOverride("profile:" + active + ":" + entry.getKey(), entry.getKey(),
                            definition.validate(entry.getValue()), MechanicContext.global(),
                            "profile:" + active, 100, -1, definition.restartRequired()));
                } catch (RuntimeException ignored) {
                }
            }
        }
        resolver.replaceLayer(100, overrides);
    }

    private Map<String, Object> flatten(String id, Set<String> visiting) {
        MechanicProfile profile = profiles.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("unknown profile " + id);
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("profile inheritance cycle at " + id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (profile.extendsProfile() != null) {
            if (!profiles.containsKey(profile.extendsProfile())) {
                throw new IllegalArgumentException("unknown parent profile " + profile.extendsProfile());
            }
            result.putAll(flatten(profile.extendsProfile(), visiting));
        }
        result.putAll(profile.overrides());
        visiting.remove(id);
        return result;
    }

    private MechanicProfile parse(String id, Map<String, Object> values) {
        if (!id.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid profile id");
        }
        String parent = values.get("extends") == null ? null : String.valueOf(values.get("extends"));
        Object rawOverrides = values.getOrDefault("overrides", values.getOrDefault("mechanics", Map.of()));
        if (!(rawOverrides instanceof Map<?, ?> overrides)) {
            throw new IllegalArgumentException("overrides must be a mapping");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : overrides.entrySet()) {
            if (entry.getKey() instanceof String key) {
                normalized.put(key, entry.getValue());
            }
        }
        return new MechanicProfile(id, parent, normalized, String.valueOf(values.getOrDefault("description", "")));
    }

    private Map<String, Object> stringMap(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private void persistActive() {
        Map<String, Object> document = new LinkedHashMap<>(config.document(FrameworkConfigService.PROFILES_FILE));
        document.put("active", active);
        config.writeDocument(FrameworkConfigService.PROFILES_FILE, document);
    }

    private void persistAll() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", FrameworkConfigService.CONFIG_VERSION);
        document.put("active", active);
        Map<String, Object> serialized = new LinkedHashMap<>();
        for (MechanicProfile profile : profiles.values()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("description", profile.description());
            if (profile.extendsProfile() != null) {
                value.put("extends", profile.extendsProfile());
            }
            value.put("overrides", profile.overrides());
            serialized.put(profile.id(), value);
        }
        document.put("profiles", serialized);
        config.writeDocument(FrameworkConfigService.PROFILES_FILE, document);
    }
}
