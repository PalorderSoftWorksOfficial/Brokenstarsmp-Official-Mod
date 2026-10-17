package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.Objects;

public record MechanicOverride(
        String id,
        String mechanicId,
        Object value,
        MechanicContext selector,
        String source,
        int layer,
        long expiresAtTick,
        boolean restartRequired
) {
    public MechanicOverride {
        id = Objects.requireNonNull(id, "id");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        selector = selector == null ? MechanicContext.global() : selector;
        source = source == null ? "api" : source;
    }

    public boolean activeAt(long tick) {
        return expiresAtTick < 0 || tick <= expiresAtTick;
    }

    public MechanicOverride withValue(Object newValue) {
        return new MechanicOverride(id, mechanicId, newValue, selector, source, layer, expiresAtTick, restartRequired);
    }
}
