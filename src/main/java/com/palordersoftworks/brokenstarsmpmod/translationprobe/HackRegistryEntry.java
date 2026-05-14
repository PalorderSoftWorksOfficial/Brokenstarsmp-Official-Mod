package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.annotations.SerializedName;

/**
 * One hack/client entry from the {@code hacks} object; map key is the hack {@link #id} field equivalent.
 */
public final class HackRegistryEntry {
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
}
