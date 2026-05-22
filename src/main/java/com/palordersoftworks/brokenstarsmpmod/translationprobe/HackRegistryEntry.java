package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.annotations.SerializedName;

/**
 * One hack/client entry from the {@code hacks} object; map key is the hack id.
 */
public final class HackRegistryEntry {
    private static final char FALLBACK_OPEN = '\u27e6';
    private static final char FALLBACK_CLOSE = '\u27e7';

    public String id;
    @SerializedName("display-name")
    public String displayName = "";
    public String key = "";
    public HackProbeMode mode = HackProbeMode.KEYBIND;

    public void ensureId(String mapKey) {
        if (id == null || id.isBlank()) {
            id = mapKey;
        }
    }

    /** CheckHacks-style missing-translation fallback: {@code ⟨NO_METEOR_CLIENT⟩}. */
    public static String fallbackFor(String hackId) {
        if (hackId == null || hackId.isBlank()) {
            return String.valueOf(FALLBACK_OPEN) + "NO_UNKNOWN" + FALLBACK_CLOSE;
        }
        return FALLBACK_OPEN + "NO_" + hackId.toUpperCase().replace('-', '_') + FALLBACK_CLOSE;
    }

    public String fallback() {
        return fallbackFor(id);
    }
}
