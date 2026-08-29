package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import java.util.Locale;

/**
 * Classifies client-submitted sign lines using CheckHacks {@code evaluateResponse} rules.
 */
public final class HackProbeClassifier {
    public static final String CONTROL_KEYBIND = "key.forward";

    private HackProbeClassifier() {}

    public static boolean isExploitPreventer(String controlLine) {
        if (controlLine == null) {
            return false;
        }
        return controlLine.strip().equalsIgnoreCase(CONTROL_KEYBIND);
    }

    public static HackProbeResultState evaluate(String responseLine, HackRegistryEntry hack, boolean exploitPreventer) {
        if (hack == null) {
            return HackProbeResultState.SKIPPED;
        }
        String resp = responseLine == null ? "" : responseLine.strip();
        if (resp.isEmpty()) {
            return HackProbeResultState.NOT_DETECTED;
        }

        String key = hack.key == null ? "" : hack.key.strip();
        String fallback = hack.fallback();

        return switch (hack.mode) {
            case METEOR -> evaluateMeteor(resp, key, fallback);
            case TRANSLATE -> evaluateTranslate(resp, key, fallback);
            case KEYBIND -> evaluateKeybind(resp, key, exploitPreventer);
        };
    }

    private static HackProbeResultState evaluateMeteor(String resp, String key, String fallback) {
        if (resp.equalsIgnoreCase(key)) {
            return HackProbeResultState.DETECTED;
        }
        if (resp.toLowerCase(Locale.ROOT).startsWith(fallback.toLowerCase(Locale.ROOT))) {
            return HackProbeResultState.NOT_DETECTED;
        }
        return HackProbeResultState.DETECTED;
    }

    private static HackProbeResultState evaluateTranslate(String resp, String key, String fallback) {
        if (resp.toLowerCase(Locale.ROOT).startsWith(fallback.toLowerCase(Locale.ROOT))) {
            return HackProbeResultState.NOT_DETECTED;
        }
        if (resp.equalsIgnoreCase(key)) {
            return HackProbeResultState.PROTECTED;
        }
        return HackProbeResultState.DETECTED;
    }

    private static HackProbeResultState evaluateKeybind(String resp, String key, boolean exploitPreventer) {
        if (exploitPreventer && resp.equalsIgnoreCase(key)) {
            return HackProbeResultState.PROTECTED;
        }
        if (resp.equalsIgnoreCase(key)) {
            return HackProbeResultState.NOT_DETECTED;
        }
        return HackProbeResultState.DETECTED;
    }
}
