package com.palordersoftworks.luaj.sandboxing;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SandboxProcess implements AutoCloseable {
    public record ExecutionResult(boolean success, boolean suspicious, boolean timeout, int exitCode, String output, String error) {
    }

    private static final int OUTPUT_LIMIT_BYTES = 1024 * 1024;

    private final Process process;
    private final BufferedWriter writer;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final Thread pumpThread;
    private volatile boolean closed;
    private volatile boolean executed;

    private SandboxProcess(Process process) {
        this.process = process;
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.pumpThread = new Thread(this::pumpOutput, "lua-sandbox-stdout");
        this.pumpThread.setDaemon(true);
        this.pumpThread.start();
    }

    public static SandboxProcess start(long memoryMb) throws IOException {
        String javaExe = javaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.add("-Xmx" + memoryMb + "m");
        command.add("-XX:+UseSerialGC");
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.palordersoftworks.luaj.sandboxing.SandboxWorker");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        return new SandboxProcess(pb.start());
    }

    public synchronized ExecutionResult execute(String code, Duration timeout) throws Exception {
        if (executed) {
            throw new IllegalStateException("sandbox process already used");
        }
        executed = true;

        writer.write(code);
        writer.write('\n');
        writer.flush();
        writer.close();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            destroyForcibly();
            joinPump();
            return new ExecutionResult(false, false, true, -1, output(), "timeout");
        }

        joinPump();

        int exit = process.exitValue();
        String out = output();
        boolean suspicious = exit == 42;
        boolean success = exit == 0;
        String error = success ? null : (suspicious ? "suspicious behavior detected" : "exit code " + exit);

        return new ExecutionResult(success, suspicious, false, exit, out, error);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        destroyForcibly();
        joinPump();
    }

    private void pumpOutput() {
        try (InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                if (total < OUTPUT_LIMIT_BYTES) {
                    int allowed = Math.min(read, OUTPUT_LIMIT_BYTES - total);
                    captured.write(buffer, 0, allowed);
                    total += allowed;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String output() {
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }

    private void destroyForcibly() {
        try {
            writer.close();
        } catch (Exception ignored) {
        }
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void joinPump() {
        try {
            pumpThread.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        String exe = isWindows() ? "java.exe" : "java";
        return Path.of(home, "bin", exe).toString();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}