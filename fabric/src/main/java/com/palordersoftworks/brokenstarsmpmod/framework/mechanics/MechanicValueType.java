package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum MechanicValueType {
    BOOLEAN,
    INTEGER,
    NUMBER,
    MULTIPLIER,
    DURATION,
    PROBABILITY,
    STRING,
    ENUM,
    LIST,
    SET,
    MAP,
    RESOURCE_ID,
    TAG,
    PREDICATE,
    NESTED;

    public Object parse(Object value) {
        return switch (this) {
            case BOOLEAN -> parseBoolean(value);
            case INTEGER -> parseInteger(value);
            case NUMBER, MULTIPLIER -> parseDouble(value);
            case DURATION -> parseDuration(value);
            case PROBABILITY -> parseProbability(value);
            case STRING, ENUM -> requireString(value);
            case LIST -> parseList(value);
            case SET -> parseSet(value);
            case MAP, PREDICATE, NESTED -> parseMap(value);
            case RESOURCE_ID -> parseResourceId(value, false);
            case TAG -> parseResourceId(value, true);
        };
    }

    public boolean isNumeric() {
        return this == INTEGER || this == NUMBER || this == MULTIPLIER || this == DURATION || this == PROBABILITY;
    }

    public boolean isCollection() {
        return this == LIST || this == SET || this == MAP || this == PREDICATE || this == NESTED;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if (stringValue.equalsIgnoreCase("true")) {
                return true;
            }
            if (stringValue.equalsIgnoreCase("false")) {
                return false;
            }
        }
        throw new IllegalArgumentException("expected boolean but got " + value);
    }

    private static long parseInteger(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("expected integer but got " + value);
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue != Math.rint(doubleValue)) {
            throw new IllegalArgumentException("expected finite integer but got " + value);
        }
        return number.longValue();
    }

    private static double parseDouble(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("expected number but got " + value);
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("expected finite number but got " + value);
        }
        return result;
    }

    private static long parseDuration(Object value) {
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!Double.isFinite(raw) || raw != Math.rint(raw) || (raw < 0 && raw != -1.0)) {
                throw new IllegalArgumentException("expected non-negative whole ticks but got " + value);
            }
            return number.longValue();
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("expected duration but got " + value);
        }
        String input = stringValue.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            throw new IllegalArgumentException("duration cannot be empty");
        }
        if (input.equals("-1")) {
            return -1L;
        }
        long multiplier = 1L;
        String numberPart = input;
        if (input.endsWith("ms")) {
            multiplier = 1L;
            numberPart = input.substring(0, input.length() - 2);
        } else if (input.endsWith("s")) {
            multiplier = 20L;
            numberPart = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1_200L;
            numberPart = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 72_000L;
            numberPart = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 1_728_000L;
            numberPart = input.substring(0, input.length() - 1);
        }
        try {
            double parsed = Double.parseDouble(numberPart);
            if (!Double.isFinite(parsed) || (parsed < 0 && parsed != -1.0) || parsed != Math.rint(parsed)) {
                throw new IllegalArgumentException("duration must be a non-negative whole number of ticks");
            }
            long result = Math.multiplyExact((long) parsed, multiplier);
            if (result < 0) {
                throw new IllegalArgumentException("duration is too large");
            }
            return result;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("invalid duration " + value, exception);
        }
    }

    private static double parseProbability(Object value) {
        double result = parseDouble(value);
        if (result < 0.0 || result > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1 but got " + value);
        }
        return result;
    }

    private static String requireString(Object value) {
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("expected string but got " + value);
        }
        return stringValue;
    }

    private static List<Object> parseList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("expected list but got " + value);
        }
        List<Object> result = new ArrayList<>();
        for (Object element : collection) {
            result.add(copy(element));
        }
        return List.copyOf(result);
    }

    private static Set<Object> parseSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("expected set/list but got " + value);
        }
        Set<Object> result = new LinkedHashSet<>();
        for (Object element : collection) {
            result.add(copy(element));
        }
        return Set.copyOf(result);
    }

    private static Map<String, Object> parseMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected mapping but got " + value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("mapping keys must be strings");
            }
            result.put(key, copy(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static String parseResourceId(Object value, boolean tag) {
        String result = requireString(value).trim().toLowerCase(Locale.ROOT);
        if (tag && result.startsWith("#")) {
            result = result.substring(1);
        }
        if (!result.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("expected namespaced id but got " + value);
        }
        return tag ? "#" + result : result;
    }

    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("mapping keys must be strings");
                }
                result.put(key, copy(entry.getValue()));
            }
            return Map.copyOf(result);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object element : collection) {
                result.add(copy(element));
            }
            return List.copyOf(result);
        }
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                double doubleValue = number.doubleValue();
                if (!Double.isFinite(doubleValue)) {
                    throw new IllegalArgumentException("non-finite number");
                }
                return doubleValue;
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                return number.longValue();
            }
        }
        return value;
    }
}
