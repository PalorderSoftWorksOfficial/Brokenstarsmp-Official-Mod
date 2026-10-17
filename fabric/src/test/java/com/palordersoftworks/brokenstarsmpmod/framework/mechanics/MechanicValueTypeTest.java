package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MechanicValueTypeTest {
    @Test
    void parsesDurationsAndProbabilities() {
        assertEquals(1200L, MechanicValueType.DURATION.parse("1m"));
        assertEquals(0.25, MechanicValueType.PROBABILITY.parse(0.25));
    }

    @Test
    void rejectsUnsafeNumbersAndIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> MechanicValueType.NUMBER.parse(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MechanicValueType.PROBABILITY.parse(1.5));
        assertThrows(IllegalArgumentException.class, () -> MechanicValueType.RESOURCE_ID.parse("not-an-id"));
    }

    @Test
    void negativeOneDurationIsDisabledSentinel() {
        assertEquals(-1L, MechanicValueType.DURATION.parse("-1"));
        assertEquals(-1L, MechanicValueType.DURATION.parse(-1L));
        assertThrows(IllegalArgumentException.class, () -> MechanicValueType.DURATION.parse(-2L));
    }
}
