package com.palordersoftworks.luaj.accesswidener;

import com.palordersoftworks.luaj.sandboxing.SandboxProcess;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class LuaScriptManager {
    private static final int DEFAULT_POOL_SIZE = 5;
    private static final long DEFAULT_MEMORY_LIMIT_MB = 128;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(250);

    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("lua/scripts");
    private final Map<String, ScriptHandle> scripts = new ConcurrentHashMap<>();
    private final Deque<SandboxProcess> idleWorkers = new ArrayDeque<>();
    private final Object poolLock = new Object();

    private final int poolSize;
    private final long memoryLimitMb;
    private final Duration timeout;

    private volatile Consumer<String> malwareAlertSink = message -> System.err.println(message);

    public LuaScriptManager() {
        this(DEFAULT_POOL_SIZE, DEFAULT_MEMORY_LIMIT_MB, DEFAULT_TIMEOUT);
    }

    public LuaScriptManager(int poolSize, long memoryLimitMb, Duration timeout) {
        this.poolSize = Math.max(1, poolSize);
        this.memoryLimitMb = Math.max(64, memoryLimitMb);
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        warmPool();
    }

    public void setMalwareAlertSink(Consumer<String> sink) {
        this.malwareAlertSink = sink == null ? System.err::println : sink;
    }

    public List<String> reloadAll() {
        stopAll();
        warmPool();

        List<String> loaded = new ArrayList<>();
        for (Path file : scanFiles()) {
            String name = scriptName(file);
            if (load(name, file, null)) {
                loaded.add(name);
            }
        }
        return loaded;
    }

    public boolean load(String name) {
        return load(name, (ScriptHost) null);
    }

    public boolean load(String name, ScriptHost host) {
        Path file = findFile(name);
        if (file == null) {
            return false;
        }
        return load(name, file, host);
    }

    private boolean load(String name, Path file, ScriptHost host) {
        if (!isSafeName(name)) {
            return false;
        }
        ScriptHandle handle = new ScriptHandle(name, file, host);
        scripts.put(name, handle);
        return true;
    }

    public boolean run(String name) {
        return run(name, (ScriptHost) null);
    }

    public boolean run(String name, ScriptHost host) {
        if (!isSafeName(name)) {
            return false;
        }

        ScriptHandle handle = scripts.get(name);
        if (handle == null) {
            Path file = findFile(name);
            if (file == null) {
                return false;
            }
            handle = new ScriptHandle(name, file, host);
            scripts.put(name, handle);
        } else if (host != null) {
            handle.setHost(host);
        }

        return handle.runFile();
    }

    public boolean runCode(String code) {
        return runCode(code, (ScriptHost) null);
    }

    public boolean runCode(String code, ScriptHost host) {
        ScriptHandle handle = new ScriptHandle("console", null, host);
        return handle.runCode(code);
    }

    public boolean pushInput(String name, String input) {
        ScriptHandle handle = scripts.get(name);
        if (handle == null) {
            return false;
        }
        handle.pushInput(input);
        return true;
    }

    public boolean stop(String name) {
        return scripts.remove(name) != null;
    }

    public void stopAll() {
        scripts.clear();
        synchronized (poolLock) {
            while (!idleWorkers.isEmpty()) {
                try {
                    idleWorkers.removeFirst().close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public Set<String> listLoaded() {
        return new TreeSet<>(scripts.keySet());
    }

    public Path getRoot() {
        return root;
    }

    private boolean executeSandboxed(String scriptName, ScriptHost host, String code) {
        SandboxProcess worker = borrowWorker();
        boolean returnToPool = false;

        try {
            SandboxProcess.ExecutionResult result = worker.execute(code, timeout);

            if (result.suspicious()) {
                String message = "malware alert in script '" + scriptName + "': " + result.error();
                malwareAlertSink.accept(message);
                emitError(host, message);
                emitOutput(host, result.output());
                returnToPool = worker.isAlive();
                return false;
            }

            if (!result.success()) {
                String text = result.output();
                if (text == null || text.isBlank()) {
                    text = result.error() == null ? "script failed" : result.error();
                }
                emitError(host, text);
                returnToPool = worker.isAlive() && !result.timeout();
                return false;
            }

            emitOutput(host, result.output());
            returnToPool = worker.isAlive() && !result.timeout();
            return true;
        } catch (Exception e) {
            emitError(host, stackTrace(e));
            returnToPool = false;
            return false;
        } finally {
            if (returnToPool) {
                returnWorker(worker);
            } else {
                discardWorker(worker);
            }
        }
    }

    private void emitOutput(@Nullable ScriptHost host, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (host != null) {
            host.print(text);
        } else {
            System.out.print(text);
        }
    }

    private void emitError(@Nullable ScriptHost host, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (host != null) {
            host.error(text);
        } else {
            System.err.println(text);
        }
    }

    private SandboxProcess borrowWorker() {
        synchronized (poolLock) {
            SandboxProcess worker = idleWorkers.pollFirst();
            if (worker != null) {
                return worker;
            }
        }

        try {
            return SandboxProcess.start(memoryLimitMb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void returnWorker(SandboxProcess worker) {
        if (worker == null) {
            return;
        }
        synchronized (poolLock) {
            if (idleWorkers.size() >= poolSize || !worker.isAlive()) {
                try {
                    worker.close();
                } catch (Exception ignored) {
                }
                return;
            }
            idleWorkers.addLast(worker);
        }
    }

    private void discardWorker(SandboxProcess worker) {
        if (worker == null) {
            return;
        }
        try {
            worker.close();
        } catch (Exception ignored) {
        }
        replenishPool();
    }

    private void replenishPool() {
        synchronized (poolLock) {
            if (idleWorkers.size() >= poolSize) {
                return;
            }
            try {
                idleWorkers.addLast(SandboxProcess.start(memoryLimitMb));
            } catch (IOException e) {
                System.err.println(stackTrace(e));
            }
        }
    }

    private void warmPool() {
        synchronized (poolLock) {
            while (idleWorkers.size() < poolSize) {
                try {
                    idleWorkers.addLast(SandboxProcess.start(memoryLimitMb));
                } catch (IOException e) {
                    System.err.println(stackTrace(e));
                    break;
                }
            }
        }
    }

    private List<Path> scanFiles() {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> {
                        String s = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return s.endsWith(".lua") || s.endsWith(".luau");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path findFile(String name) {
        if (!isSafeName(name)) {
            return null;
        }

        Path lua = safeResolve(name + ".lua");
        Path luau = safeResolve(name + ".luau");

        if (lua != null && Files.isRegularFile(lua, LinkOption.NOFOLLOW_LINKS)) {
            return lua;
        }
        if (luau != null && Files.isRegularFile(luau, LinkOption.NOFOLLOW_LINKS)) {
            return luau;
        }
        return null;
    }

    private Path safeResolve(String relative) {
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            return null;
        }
        return candidate;
    }

    private boolean isSafeName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.matches("[A-Za-z0-9._-]+");
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

    private final class ScriptHandle {
        private final String name;
        private final Path file;
        private volatile ScriptHost host;

        private ScriptHandle(String name, Path file, ScriptHost host) {
            this.name = name;
            this.file = file;
            this.host = host;
        }

        private void setHost(ScriptHost host) {
            this.host = host;
        }

        private boolean runFile() {
            if (file == null) {
                return false;
            }
            try {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                return executeSandboxed(name, host, code);
            } catch (Exception e) {
                reportError(e);
                return false;
            }
        }

        private boolean runCode(String code) {
            return executeSandboxed(name, host, code);
        }

        private void pushInput(String input) {
            ScriptHost current = host;
            if (current != null) {
                current.pushInput(input);
            }
        }

        private void reportError(Throwable e) {
            String text = stackTrace(e);
            ScriptHost current = host;
            if (current != null) {
                current.error(text);
            } else {
                System.err.println(text);
            }
        }
    }
}