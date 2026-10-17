package com.palordersoftworks.luaj.accesswidener;

import com.palordersoftworks.brokenstarsmpmod.framework.BrokenStarsFramework;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolution;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;

import java.util.UUID;

public final class HostApi {
    private final LuaScriptManager manager;

    public HostApi(LuaScriptManager manager) {
        this.manager = manager;
    }

    public void reloadScripts() {
        manager.reloadAll();
    }

    public void stopScript(String name) {
        manager.stop(name);
    }

    public void stopAllScripts() {
        manager.stopAll();
    }

    public Object readMechanic(String id) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        if (framework == null || !framework.registry().contains(id)) {
            return null;
        }
        MechanicResolution resolution = framework.read(id, MechanicContext.global());
        return resolution.value();
    }

    public boolean writeMechanic(String id, Object value) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        if (framework == null || !framework.registry().contains(id) || !isSafeWrite(id)) {
            return false;
        }
        try {
            framework.write(id, value, MechanicContext.global(), null, "lua");
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean applyProfile(String profile) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework != null && framework.profiles().apply(profile);
    }

    public boolean startEvent(String event) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework != null && framework.events().start(event).successful();
    }

    public boolean stopEvent(String event) {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework != null && framework.events().stop(event);
    }

    public String activeProfile() {
        BrokenStarsFramework framework = BrokenStarsFramework.getOrNull();
        return framework == null ? "unavailable" : framework.profiles().active();
    }

    public String playerGroup(String playerUuid) {
        return PermissionCompat.primaryGroup(parseUuid(playerUuid)).orElse("");
    }

    public String playerPrefix(String playerUuid) {
        return PermissionCompat.prefix(parseUuid(playerUuid)).orElse("");
    }

    public boolean playerHasPermission(String playerUuid, String node) {
        return PermissionCompat.hasPermissionNode(parseUuid(playerUuid), node);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isSafeWrite(String id) {
        return !id.startsWith("emergency") && !id.equals("safe_mode") && !id.contains("recipe");
    }
}