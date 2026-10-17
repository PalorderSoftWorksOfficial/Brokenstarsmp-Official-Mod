package com.palordersoftworks.brokenstarsmpmod.framework.config;

public record ConfigIssue(
        Severity severity,
        String file,
        String section,
        String key,
        String value,
        String expected,
        String fallback,
        String message
) {
    public enum Severity {
        WARNING,
        ERROR
    }

    public static ConfigIssue error(String file, String section, String key, Object value,
                                   String expected, String fallback, String message) {
        return new ConfigIssue(Severity.ERROR, file, section, key, String.valueOf(value), expected, fallback, message);
    }

    public static ConfigIssue warning(String file, String section, String key, Object value,
                                     String expected, String fallback, String message) {
        return new ConfigIssue(Severity.WARNING, file, section, key, String.valueOf(value), expected, fallback, message);
    }
}
