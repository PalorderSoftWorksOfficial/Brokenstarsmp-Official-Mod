package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicResolverTest {
    @Test
    void resolvesMostSpecificValidRule() {
        MechanicRegistry registry = new MechanicRegistry();
        MechanicResolver resolver = new MechanicResolver(registry);
        resolver.add(new MechanicOverride("global", "pvp_enabled", false, MechanicContext.global(), "test", 0, -1, false));
        resolver.add(new MechanicOverride("world", "pvp_enabled", true,
                MechanicContext.forWorld("minecraft:overworld", "world"), "test", 0, -1, false));
        resolver.add(new MechanicOverride("emergency", "pvp_enabled", false,
                MechanicContext.global(), "emergency", 1000, -1, false));

        MechanicContext context = MechanicContext.forWorld("minecraft:overworld", "world");
        assertEquals(false, resolver.resolve("pvp_enabled", context).value());
        assertEquals("emergency", resolver.resolve("pvp_enabled", context).source());
    }

    @Test
    void invalidOverrideDoesNotReplaceFallback() {
        MechanicRegistry registry = new MechanicRegistry();
        MechanicResolver resolver = new MechanicResolver(registry);
        resolver.replaceAll(List.of(new MechanicOverride("bad", "pvp_enabled", "not-a-boolean",
                MechanicContext.global(), "test", 0, -1, false)));
        assertTrue(resolver.overrides().isEmpty());
        assertEquals(true, resolver.resolve("pvp_enabled", MechanicContext.global()).value());
    }
}
