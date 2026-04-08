package com.palordersoftworks.luaj.accesswidener;

import net.fabricmc.loader.api.FabricLoader;
import party.iroiro.luajava.Lua;
import party.iroiro.luajava.luajit.LuaJit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LuaScriptManager {
    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("lua/scripts");
    private final Map<String, ScriptHandle> scripts = new ConcurrentHashMap<>();

    public LuaScriptManager() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<String> reloadAll() {
        stopAll();
        List<String> loaded = new ArrayList<>();
        for (Path file : scanFiles()) {
            String name = scriptName(file);
            if (load(name, file)) {
                loaded.add(name);
            }
        }
        return loaded;
    }

    public synchronized boolean load(String name) {
        Path file = findFile(name);
        if (file == null) return false;
        return load(name, file);
    }

    public synchronized boolean run(String name) {
        ScriptHandle handle = scripts.get(name);
        if (handle == null) {
            if (!load(name)) return false;
            handle = scripts.get(name);
        }
        return handle != null && handle.runFile();
    }

    public synchronized boolean runCode(String code) {
        return createRuntime("console", null).runCode(code);
    }

    public synchronized boolean stop(String name) {
        ScriptHandle handle = scripts.remove(name);
        if (handle == null) return false;
        handle.stop();
        return true;
    }

    public synchronized void stopAll() {
        for (ScriptHandle handle : scripts.values()) {
            handle.stop();
        }
        scripts.clear();
    }

    public Set<String> listLoaded() {
        return new TreeSet<>(scripts.keySet());
    }

    public Path getRoot() {
        return root;
    }

    private boolean load(String name, Path file) {
        ScriptHandle handle = createRuntime(name, file);
        if (handle == null) return false;
        scripts.put(name, handle);
        return true;
    }

    private ScriptHandle createRuntime(String name, Path file) {
        try {
            Lua lua = new LuaJit();
            lua.openLibraries();

            HostApi host = new HostApi(this);
            lua.set("host", host);
            lua.set("SCRIPT_NAME", name);
            lua.set("SCRIPT_DIR", root.toString());

            Path bootstrap = FabricLoader.getInstance()
                    .getModContainer("brokenstarsmp")
                    .orElseThrow()
                    .findPath("lua/bootstrap.lua")
                    .orElse(null);

            if (bootstrap != null && Files.exists(bootstrap)) {
                lua.run(Files.readString(bootstrap, StandardCharsets.UTF_8));
            }

            return new ScriptHandle(name, file, lua);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Path> scanFiles() {
        if (!Files.exists(root)) return List.of();
        try {
            try (var stream = Files.walk(root)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String s = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return s.endsWith(".lua") || s.endsWith(".luau");
                        })
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path findFile(String name) {
        Path lua = root.resolve(name + ".lua");
        Path luau = root.resolve(name + ".luau");
        if (Files.isRegularFile(lua)) return lua;
        if (Files.isRegularFile(luau)) return luau;
        return null;
    }

    private String scriptName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private static final class ScriptHandle {
        private final String name;
        private final Path file;
        private final Lua lua;

        private ScriptHandle(String name, Path file, Lua lua) {
            this.name = name;
            this.file = file;
            this.lua = lua;
        }

        private boolean runFile() {
            if (file == null) return false;
            try {
                lua.run(Files.readString(file, StandardCharsets.UTF_8));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean runCode(String code) {
            try {
                lua.run(code);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private void stop() {
            try {
                lua.close();
            } catch (Exception ignored) {
            }
        }
    }
}