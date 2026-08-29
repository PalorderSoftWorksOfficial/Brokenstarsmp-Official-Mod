package com.palordersoftworks.luaj.accesswidener;

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
}