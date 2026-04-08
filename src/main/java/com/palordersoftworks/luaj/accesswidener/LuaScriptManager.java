package com.palordersoftworks.luaj.accesswidener;

import net.fabricmc.loader.api.FabricLoader;
import party.iroiro.luajava.luajit.LuaJit;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
            if (load(name, file, null)) {
                loaded.add(name);
            }
        }
        return loaded;
    }

    public synchronized boolean load(String name) {
        return load(name, null);
    }

    public synchronized boolean load(String name, ScriptHost host) {
        Path file = findFile(name);
        if (file == null) return false;
        return load(name, file, host);
    }

    public synchronized boolean run(String name) {
        return run(name, null);
    }

    public synchronized boolean run(String name, ScriptHost host) {
        ScriptHandle handle = scripts.get(name);
        if (handle == null) {
            if (!load(name, host)) return false;
            handle = scripts.get(name);
        }
        if (handle == null) return false;
        return handle.runFile();
    }

    public synchronized boolean runCode(String code) {
        return runCode(code, null);
    }

    public synchronized boolean runCode(String code, ScriptHost host) {
        ScriptHandle handle = createRuntime("console", null, host);
        if (handle == null) return false;
        return handle.runCode(code);
    }

    public synchronized boolean pushInput(String name, String input) {
        ScriptHandle handle = scripts.get(name);
        if (handle == null) return false;
        handle.pushInput(input);
        return true;
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

    private boolean load(String name, Path file, ScriptHost host) {
        ScriptHandle handle = createRuntime(name, file, host);
        if (handle == null) return false;
        scripts.put(name, handle);
        return true;
    }

    private ScriptHandle createRuntime(String name, Path file, ScriptHost host) {
        try {
            LuaJit lua = new LuaJit();
            lua.openLibraries();

            if (host != null) {
                lua.set("host", host);
            }

            lua.set("SCRIPT_NAME", name);
            lua.set("SCRIPT_DIR", root.toString());

            Path bootstrap = FabricLoader.getInstance()
                    .getModContainer("brokenstarsmp")
                    .orElseThrow()
                    .findPath("src/main/lua/apis/bootstrap.lua")
                    .orElse(null);

            if (bootstrap != null && Files.exists(bootstrap)) {
                lua.run(Files.readString(bootstrap, StandardCharsets.UTF_8));
            }

            return new ScriptHandle(name, file, lua, host);
        } catch (Exception e) {
            if (host != null) {
                host.error(stackTrace(e));
            } else {
                System.err.println(stackTrace(e));
            }
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

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private record ScriptHandle(String name, Path file, LuaJit lua, ScriptHost host) {

        private boolean runFile() {
            if (file == null) return false;
            try {
                lua.run(Files.readString(file, StandardCharsets.UTF_8));
                return true;
            } catch (Exception e) {
                reportError(e);
                return false;
            }
        }

        private boolean runCode(String code) {
            try {
                lua.run(code);
                return true;
            } catch (Exception e) {
                reportError(e);
                return false;
            }
        }

        private void pushInput(String input) {
            if (host != null) {
                host.pushInput(input);
            }
        }

        private void reportError(Throwable e) {
            String text = stackTrace(e);
            if (host != null) {
                host.error(text);
            } else {
                System.err.println(text);
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