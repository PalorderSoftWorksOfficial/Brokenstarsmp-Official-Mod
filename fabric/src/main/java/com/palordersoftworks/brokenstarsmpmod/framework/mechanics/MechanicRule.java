package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.Objects;

public record MechanicRule(
        String mechanicId,
        Object value,
        MechanicContext selector,
        String file,
        String section,
        int priority,
        boolean restartRequired
) {
    public MechanicRule {
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        selector = selector == null ? MechanicContext.global() : selector;
        file = file == null ? "" : file;
        section = section == null ? "" : section;
    }

    public MechanicOverride toOverride(String id) {
        return toOverride(id, priority);
    }

    public MechanicOverride toOverride(String id, int layer) {
        return new MechanicOverride(id, mechanicId, value, selector, file + ":" + section, layer, -1, restartRequired);
    }
}
