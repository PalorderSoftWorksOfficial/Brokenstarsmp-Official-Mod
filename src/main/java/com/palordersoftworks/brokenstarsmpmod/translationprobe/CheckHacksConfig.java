package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root JSON for {@code config/brokenstarsmp/translation_probe_config.json} (check-hacks registry + settings).
 */
public final class CheckHacksConfig {
    public boolean enabled = false;

    @SerializedName("default-check-hacks")
    public List<String> defaultCheckHacks = new ArrayList<>();

    @SerializedName("auto-check-on-join")
    public boolean autoCheckOnJoin = false;

    @SerializedName("detect-flag")
    public boolean detectFlag = false;

    @SerializedName("command-if-positive")
    public String commandIfPositive = "";

    @SerializedName("command-if-protected")
    public String commandIfProtected = "";

    @SerializedName("command-if-clean")
    public String commandIfClean = "";

    @SerializedName("timeout-ticks")
    public int timeoutTicks = 200;

    @SerializedName("between-sign-ticks")
    public int betweenSignTicks = 40;

    /** Hack id -> entry (ids must match keys exactly). */
    public Map<String, HackRegistryEntry> hacks = new LinkedHashMap<>();

    /**
     * Built-in registry used when no file exists or when {@link #hacks} is empty after load.
     */
    public static CheckHacksConfig createDefaultRegistry() {
        CheckHacksConfig c = new CheckHacksConfig();
        c.enabled = false;
        c.autoCheckOnJoin = false;
        c.detectFlag = false;
        c.timeoutTicks = 200;
        c.betweenSignTicks = 40;
        c.commandIfPositive = "";
        c.commandIfProtected = "";
        c.commandIfClean = "";
        c.hacks = new LinkedHashMap<>();
        put(c, "meteor-client", "Meteor Client", "key.meteor-client.open-gui", HackProbeMode.METEOR);
        put(c, "liquidbounce", "LiquidBounce", "liquidbounce.module.killaura.name", HackProbeMode.TRANSLATE);
        put(c, "freecam", "Freecam", "key.freecam.toggle", HackProbeMode.KEYBIND);
        put(c, "wurst", "Wurst Client (-1.21)", "key.wurst.zoom", HackProbeMode.KEYBIND);
        put(c, "xray-fabric", "XRay (Fabric)", "xray.config.toggle", HackProbeMode.KEYBIND);
        put(c, "chestesp", "ChestESP", "key.chestesp.toggle", HackProbeMode.KEYBIND);
        put(c, "killaura-fabric", "KillAura (Fabric)", "key.killaura", HackProbeMode.KEYBIND);
        put(c, "autofish", "AutoFish", "key.autofish.open_gui", HackProbeMode.KEYBIND);
        put(c, "lumina", "Lumina", "key.lumina.open_click_gui", HackProbeMode.KEYBIND);
        put(c, "autoswitch", "AutoSwitch", "key.autoswitch.toggle", HackProbeMode.KEYBIND);
        put(c, "bleachhack", "BleachHack", "bleachhack.module.killaura", HackProbeMode.TRANSLATE);
        put(c, "aristois", "Aristois", "emc.module.killaura.name", HackProbeMode.TRANSLATE);
        put(c, "coffee", "Coffee Client", "coffee.module.killaura.name", HackProbeMode.TRANSLATE);
        put(c, "world-downloader", "World Downloader", "key.wdl.startStop", HackProbeMode.TRANSLATE);
        put(c, "autoclicker-fabric", "AutoClicker (Fabric)", "autoclicker-fabric.hud.holding", HackProbeMode.TRANSLATE);
        put(c, "antiafk", "AntiAFK", "key.antiafk.toggle", HackProbeMode.TRANSLATE);
        put(c, "auto-clicker-mc", "Auto Clicker (p1k0chu)", "key.auto-clicker_.toggle", HackProbeMode.KEYBIND);
        c.defaultCheckHacks = new ArrayList<>(c.hacks.keySet());
        return c;
    }

    private static void put(CheckHacksConfig c, String id, String displayName, String key, HackProbeMode mode) {
        HackRegistryEntry e = new HackRegistryEntry();
        e.id = id;
        e.displayName = displayName;
        e.key = key;
        e.mode = mode;
        c.hacks.put(id, e);
    }

    public void mergeMissingHacksFrom(CheckHacksConfig defaults) {
        if (defaults == null || defaults.hacks == null) {
            return;
        }
        if (hacks == null) {
            hacks = new LinkedHashMap<>();
        }
        for (var e : defaults.hacks.entrySet()) {
            hacks.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    public void normalizeHackIds() {
        if (hacks == null) {
            return;
        }
        for (var e : hacks.entrySet()) {
            if (e.getValue() != null) {
                e.getValue().ensureId(e.getKey());
            }
        }
    }

    public HackRegistryEntry getHack(String id) {
        if (id == null || hacks == null) {
            return null;
        }
        return hacks.get(id);
    }
}
