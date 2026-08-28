package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CheckHacksConfig {
    public boolean enabled = true;

    @SerializedName("default-check-hacks")
    public List<String> defaultCheckHacks = new ArrayList<>();

    @SerializedName("auto-check-on-join")
    public AutoCheckOnJoin autoCheckOnJoin = new AutoCheckOnJoin();

    @SerializedName("detect-flag")
    public DetectFlag detectFlag = new DetectFlag();

    @SerializedName("command-if-positive")
    public CommandEntry commandIfPositive = new CommandEntry();

    @SerializedName("command-if-protected")
    public CommandEntry commandIfProtected = new CommandEntry();

    @SerializedName("command-if-clean")
    public CommandEntry commandIfClean = new CommandEntry();

    @SerializedName("join-delay-ticks")
    public int joinDelayTicks = 0;

    @SerializedName("open-sign-delay-ticks")
    public int openSignDelayTicks = 1;

    @SerializedName("timeout-ticks")
    public int timeoutTicks = 40;

    @SerializedName("between-sign-ticks")
    public int betweenSignTicks = 1;

    public Bedrock bedrock = new Bedrock();
    public Map<String, HackRegistryEntry> hacks = new LinkedHashMap<>();

    public static final class AutoCheckOnJoin {
        public boolean enabled = true;
        @SerializedName("only-first-join")
        public boolean onlyFirstJoin = false;
        public List<String> hacks = new ArrayList<>();
    }

    public static final class DetectFlag {
        public boolean enabled = true;
        public Map<String, Boolean> anticheats = new LinkedHashMap<>();
        @SerializedName("cooldown-hours")
        public long cooldownHours = 24;
        public List<String> hacks = new ArrayList<>();
    }

    public static final class CommandEntry {
        public boolean enabled = false;
        public String command = "";
    }

    public static final class Bedrock {
        public boolean enabled = true;
        public List<String> prefixes = new ArrayList<>(List.of(".", "*"));
    }

    public static CheckHacksConfig createDefaultRegistry() {
        CheckHacksConfig c = new CheckHacksConfig();
        c.enabled = true;
        c.joinDelayTicks = 0;
        c.openSignDelayTicks = 1;
        c.timeoutTicks = 40;
        c.betweenSignTicks = 1;
        c.commandIfPositive = new CommandEntry();
        c.commandIfProtected = new CommandEntry();
        c.commandIfClean = new CommandEntry();
        c.bedrock = new Bedrock();
        c.hacks = new LinkedHashMap<>();

        put(c, "meteor-client", "Meteor Client", "key.meteor-client.open-gui", HackProbeMode.METEOR);
        put(c, "meteor-client-commands", "Meteor Client Commands", "key.meteor-client.open-commands", HackProbeMode.METEOR);
        put(c, "liquidbounce", "LiquidBounce", "liquidbounce.module.killaura.name", HackProbeMode.TRANSLATE);
        put(c, "liquidbounce-enabled", "LiquidBounce Enabled", "liquidbounce.generic.enabled", HackProbeMode.TRANSLATE);
        put(c, "freecam", "Freecam", "key.freecam.toggle", HackProbeMode.KEYBIND);
        put(c, "zergatul-freecam", "Zergatul Freecam", "key.zergatul.freecam.toggle", HackProbeMode.KEYBIND);
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
        put(c, "autoclicker-rinf", "AutoClicker RINF", "key.autoclicker-rinf.toggleacf", HackProbeMode.KEYBIND);
        put(c, "antiafk", "AntiAFK", "key.antiafk.toggle", HackProbeMode.TRANSLATE);
        put(c, "auto-clicker-mc", "Auto Clicker (p1k0chu)", "key.auto-clicker_.toggle", HackProbeMode.KEYBIND);
        put(c, "hypnotic-client", "Hypnotic Client", "key.hypnotic-client.open-click-gui", HackProbeMode.KEYBIND);
        put(c, "autoattack", "AutoAttack", "key.autoattack.afkAttack", HackProbeMode.KEYBIND);
        put(c, "flymod", "Flymod", "key.flymod.toggle", HackProbeMode.KEYBIND);
        put(c, "flighthelper", "FlightHelper", "key.flighthelper.lockup", HackProbeMode.KEYBIND);
        put(c, "climbladdersfast", "Climb Ladders Fast", "key.climbladdersfast.toggle", HackProbeMode.KEYBIND);
        put(c, "stepup", "StepUp", "key.stepup.toggle", HackProbeMode.KEYBIND);
        put(c, "playerhighlighter", "PlayerHighlighter", "key.playerhighlighter.toggle", HackProbeMode.KEYBIND);

        c.defaultCheckHacks = new ArrayList<>(c.hacks.keySet());
        c.autoCheckOnJoin = new AutoCheckOnJoin();
        c.autoCheckOnJoin.hacks = new ArrayList<>(List.of(
                "meteor-client", "liquidbounce", "freecam", "wurst",
                "bleachhack", "aristois", "world-downloader", "autoclicker-fabric", "antiafk"
        ));
        c.detectFlag = new DetectFlag();
        c.detectFlag.anticheats.put("grimac", true);
        c.detectFlag.hacks = new ArrayList<>(c.autoCheckOnJoin.hacks);
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

    public void normalizeAfterLoad() {
        if (defaultCheckHacks == null) {
            defaultCheckHacks = new ArrayList<>();
        }
        if (autoCheckOnJoin == null) {
            autoCheckOnJoin = new AutoCheckOnJoin();
        }
        if (autoCheckOnJoin.hacks == null) {
            autoCheckOnJoin.hacks = new ArrayList<>();
        }
        if (detectFlag == null) {
            detectFlag = new DetectFlag();
        }
        if (detectFlag.anticheats == null) {
            detectFlag.anticheats = new LinkedHashMap<>();
        }
        if (detectFlag.hacks == null) {
            detectFlag.hacks = new ArrayList<>();
        }
        if (commandIfPositive == null) {
            commandIfPositive = new CommandEntry();
        }
        if (commandIfProtected == null) {
            commandIfProtected = new CommandEntry();
        }
        if (commandIfClean == null) {
            commandIfClean = new CommandEntry();
        }
        if (bedrock == null) {
            bedrock = new Bedrock();
        }
        if (bedrock.prefixes == null || bedrock.prefixes.isEmpty()) {
            bedrock.prefixes = new ArrayList<>(List.of(".", "*"));
        }
        joinDelayTicks = Math.max(0, joinDelayTicks);
        openSignDelayTicks = Math.max(0, openSignDelayTicks);
        timeoutTicks = Math.max(1, timeoutTicks);
        betweenSignTicks = Math.max(0, betweenSignTicks);
        normalizeHackIds();
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

    public List<String> resolveHackIds(List<String> ids) {
        List<String> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (getHack(id.trim()) != null) {
                out.add(id.trim());
            }
        }
        return out;
    }
}
