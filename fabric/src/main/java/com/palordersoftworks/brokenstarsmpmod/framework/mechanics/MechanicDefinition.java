package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.Objects;
import java.util.Set;

public record MechanicDefinition(
        String id,
        MechanicValueType type,
        Object defaultValue,
        Double minimum,
        Double maximum,
        Set<String> allowedValues,
        boolean restartRequired,
        String description
) {
    public MechanicDefinition {
        id = requireId(id);
        type = Objects.requireNonNull(type, "type");
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
        description = description == null ? "" : description;
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("minimum cannot exceed maximum for " + id);
        }
        if (defaultValue != null) {
            defaultValue = type.parse(defaultValue);
            validateBounds(id, type, defaultValue, minimum, maximum, allowedValues);
        }
    }

    public static MechanicDefinition of(String id, MechanicValueType type, Object defaultValue) {
        return new MechanicDefinition(id, type, defaultValue, null, null, Set.of(), false, "");
    }

    public static MechanicDefinition bounded(String id, MechanicValueType type, Object defaultValue,
                                               double minimum, double maximum, boolean restartRequired,
                                               String description) {
        return new MechanicDefinition(id, type, defaultValue, minimum, maximum, Set.of(), restartRequired, description);
    }

    public Object validate(Object value) {
        Object parsed = type.parse(value);
        validateBounds(id, type, parsed, minimum, maximum, allowedValues);
        return parsed;
    }

    public boolean isValid(Object value) {
        try {
            validate(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void validateBounds(String id, MechanicValueType type, Object value,
                                        Double minimum, Double maximum, Set<String> allowedValues) {
        if (value instanceof Number number && (minimum != null || maximum != null)) {
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric)) {
                throw new IllegalArgumentException("non-finite value for " + id);
            }
            if (minimum != null && numeric < minimum) {
                throw new IllegalArgumentException("value for " + id + " is below " + minimum);
            }
            if (maximum != null && numeric > maximum) {
                throw new IllegalArgumentException("value for " + id + " is above " + maximum);
            }
        }
        if (type == MechanicValueType.PROBABILITY && value instanceof Number number) {
            double numeric = number.doubleValue();
            if (numeric < 0.0 || numeric > 1.0) {
                throw new IllegalArgumentException("probability for " + id + " must be between 0 and 1");
            }
        }
        if (!allowedValues.isEmpty() && value instanceof String stringValue
                && !allowedValues.contains(stringValue)) {
            throw new IllegalArgumentException("value for " + id + " must be one of " + allowedValues);
        }
    }

    private static String requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid mechanic id: " + id);
        }
        return id;
    }
}
