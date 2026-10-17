package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

public record MechanicResolution(
        String mechanicId,
        Object value,
        Object defaultValue,
        String source,
        MechanicScope scope,
        boolean restartRequired
) {
}
