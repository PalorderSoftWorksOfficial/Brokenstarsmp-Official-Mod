package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.Locale;

public enum MechanicScope {
    GLOBAL(0),
    DIMENSION(10),
    WORLD(20),
    REGION(30),
    BLOCK(35),
    PERMISSION(40),
    RANK(50),
    TEAM(60),
    PLAYER(70),
    ENTITY(80),
    ITEM(90);

    private final int specificity;

    MechanicScope(int specificity) {
        this.specificity = specificity;
    }

    public int specificity() {
        return specificity;
    }

    public static MechanicScope from(String value) {
        if (value == null) {
            return GLOBAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown mechanic scope: " + value, exception);
        }
    }
}
