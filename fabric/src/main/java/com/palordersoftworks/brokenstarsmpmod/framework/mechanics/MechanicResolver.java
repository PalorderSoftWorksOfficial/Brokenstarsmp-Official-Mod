package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MechanicResolver {
    private final MechanicRegistry registry;
    private volatile List<MechanicOverride> overrides = List.of();

    public MechanicResolver(MechanicRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public MechanicRegistry registry() {
        return registry;
    }

    public synchronized void replaceAll(Collection<MechanicOverride> replacements) {
        List<MechanicOverride> valid = new ArrayList<>();
        if (replacements != null) {
            for (MechanicOverride override : replacements) {
                if (override != null && registry.contains(override.mechanicId())
                        && registry.get(override.mechanicId()).isValid(override.value())) {
                    valid.add(override.withValue(registry.validate(override.mechanicId(), override.value())));
                }
            }
        }
        valid.sort(comparator());
        overrides = List.copyOf(valid);
    }

    public synchronized void replaceLayer(int layer, Collection<MechanicOverride> replacements) {
        List<MechanicOverride> next = new ArrayList<>(overrides);
        next.removeIf(existing -> existing.layer() == layer);
        if (replacements != null) {
            for (MechanicOverride override : replacements) {
                if (override != null && registry.contains(override.mechanicId())
                        && registry.get(override.mechanicId()).isValid(override.value())) {
                    next.add(override.withValue(registry.validate(override.mechanicId(), override.value())));
                }
            }
        }
        next.sort(comparator());
        overrides = List.copyOf(next);
    }

    public synchronized MechanicOverride add(MechanicOverride override) {
        if (override == null) {
            throw new IllegalArgumentException("override cannot be null");
        }
        MechanicDefinition definition = registry.require(override.mechanicId());
        MechanicOverride validated = override.withValue(definition.validate(override.value()));
        List<MechanicOverride> next = new ArrayList<>(overrides);
        next.removeIf(existing -> existing.id().equals(validated.id()));
        next.add(validated);
        next.sort(comparator());
        overrides = List.copyOf(next);
        return validated;
    }

    public synchronized boolean remove(String id) {
        List<MechanicOverride> next = new ArrayList<>(overrides);
        boolean removed = next.removeIf(existing -> existing.id().equals(id));
        if (removed) {
            overrides = List.copyOf(next);
        }
        return removed;
    }

    public synchronized int removeSource(String source) {
        List<MechanicOverride> next = new ArrayList<>(overrides);
        int removed = (int) next.stream().filter(existing -> existing.source().equals(source)).count();
        next.removeIf(existing -> existing.source().equals(source));
        if (removed > 0) {
            overrides = List.copyOf(next);
        }
        return removed;
    }

    public MechanicOverride write(String mechanicId, Object value, MechanicContext selector, String source) {
        MechanicDefinition definition = registry.require(mechanicId);
        return add(new MechanicOverride(UUID.randomUUID().toString(), mechanicId, definition.validate(value),
                selector == null ? MechanicContext.global() : selector, source, 500, -1, definition.restartRequired()));
    }

    public MechanicResolution resolve(String mechanicId, MechanicContext context) {
        MechanicDefinition definition = registry.require(mechanicId);
        MechanicContext effectiveContext = context == null ? MechanicContext.global() : context;
        for (MechanicOverride override : overrides) {
            if (!override.mechanicId().equals(mechanicId) || !override.activeAt(Long.MAX_VALUE)) {
                continue;
            }
            if (!override.selector().matches(effectiveContext)) {
                continue;
            }
            Object value = definition.validate(override.value());
            return new MechanicResolution(mechanicId, value, definition.defaultValue(), override.source(),
                    override.selector().scope(), override.restartRequired());
        }
        return new MechanicResolution(mechanicId, definition.defaultValue(), definition.defaultValue(), "default",
                MechanicScope.GLOBAL, definition.restartRequired());
    }

    public MechanicResolution inspect(String mechanicId, MechanicContext context) {
        return resolve(mechanicId, context);
    }

    public List<MechanicOverride> overrides() {
        return overrides;
    }

    public int size() {
        return overrides.size();
    }

    public void clear() {
        overrides = List.of();
    }

    private static Comparator<MechanicOverride> comparator() {
        return Comparator.comparingInt(MechanicOverride::layer).reversed()
                .thenComparing(override -> override.selector().specificity(), Comparator.reverseOrder())
                .thenComparing(MechanicOverride::restartRequired)
                .thenComparing(MechanicOverride::source)
                .thenComparing(MechanicOverride::id);
    }
}
