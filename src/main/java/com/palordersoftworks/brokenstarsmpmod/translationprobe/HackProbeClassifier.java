package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Classifies client-submitted sign text for {@link HackProbeMode}.
 */
public final class HackProbeClassifier {
    private HackProbeClassifier() {}

    /**
     * @param line          trimmed line from the configured sign row (index 0)
     * @param expectedKey   translation / keybind key
     * @param mode          strategy
     * @param displayName   registry display name (for TRANSLATE)
     */
    public static HackProbeResultState classifyLine(String line, String expectedKey, HackProbeMode mode, String displayName) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty()) {
            return HackProbeResultState.PROTECTED;
        }
        String key = expectedKey == null ? "" : expectedKey.trim();
        String resolved = Text.translatable(key).getString().trim();

        if (matchesKeybindStrict(t, key, resolved)) {
            return HackProbeResultState.CLEAN;
        }

        return switch (mode) {
            case METEOR -> classifyMeteor(t, key, resolved);
            case KEYBIND -> HackProbeResultState.FLAGGED;
            case TRANSLATE -> classifyTranslate(t, key, resolved, displayName);
        };
    }

    private static HackProbeResultState classifyMeteor(String line, String key, String resolved) {
        if (matchesKeybindStrict(line, key, resolved)) {
            return HackProbeResultState.CLEAN;
        }
        String rl = resolved.toLowerCase(Locale.ROOT);
        String ll = line.toLowerCase(Locale.ROOT);
        if (rl.contains("meteor") && ll.contains("meteor")) {
            return HackProbeResultState.CLEAN;
        }
        return HackProbeResultState.FLAGGED;
    }

    private static HackProbeResultState classifyTranslate(String line, String key, String resolved, String displayName) {
        if (matchesKeybindStrict(line, key, resolved)) {
            return HackProbeResultState.CLEAN;
        }
        if (displayName != null && !displayName.isBlank() && line.trim().equalsIgnoreCase(displayName.trim())) {
            return HackProbeResultState.CLEAN;
        }
        return HackProbeResultState.FLAGGED;
    }

    private static boolean matchesKeybindStrict(String line, String key, String resolved) {
        String t = line.trim();
        return t.equals(key) || t.equals(resolved);
    }
}
