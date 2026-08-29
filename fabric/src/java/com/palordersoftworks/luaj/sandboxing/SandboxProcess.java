package com.palordersoftworks.luaj.sandboxing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SandboxProcess implements AutoCloseable {
    public record ExecutionResult(
            boolean success,
            boolean suspicious,
            boolean timeout,
            int exitCode,
            String output,
            String error
    ) {
    }

    private static final int LIMIT_BYTES = 1024 * 1024;

    private final Process process;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ByteArrayOutputStream stderrCaptured = new ByteArrayOutputStream();
    private final Thread stderrPump;
    private volatile boolean closed;

    private SandboxProcess(Process process) {
        this.process = process;
        this.in = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));

        this.stderrPump = new Thread(this::pumpStderr, "lua-sandbox-stderr");
        this.stderrPump.setDaemon(true);
        this.stderrPump.start();
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
        pb.redirectErrorStream(false);
        return new SandboxProcess(pb.start());
    }

    public synchronized boolean isAlive() {
        return !closed && process.isAlive();
    }

    public synchronized ExecutionResult execute(String code, Duration timeout) throws Exception {
        if (closed) {
            throw new IllegalStateException("sandbox process is closed");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        if (!process.isAlive()) {
            return new ExecutionResult(
                    false,
                    false,
                    false,
                    exitCodeOrMinusOne(),
                    "",
                    diagnosticsOr("sandbox worker already exited")
            );
        }

        byte[] payload = code == null ? new byte[0] : code.getBytes(StandardCharsets.UTF_8);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lua-sandbox-timeout");
            t.setDaemon(true);
            return t;
        });

        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledFuture<?> killer = scheduler.schedule(() -> {
            timedOut.set(true);
            destroyForcibly();
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();

            boolean success = in.readBoolean();
            boolean suspicious = in.readBoolean();
            boolean timeoutFlag = in.readBoolean();
            int exitCode = in.readInt();
            String output = readString(in);
            String error = readString(in);

            return new ExecutionResult(success, suspicious, timeoutFlag, exitCode, output, error);
        } catch (EOFException eof) {
            if (timedOut.get()) {
                return new ExecutionResult(false, false, true, -1, "", "timeout");
            }
            return new ExecutionResult(false, false, false, exitCodeOrMinusOne(), "", diagnosticsOr("sandbox worker terminated unexpectedly"));
        } catch (IOException io) {
            if (timedOut.get()) {
                return new ExecutionResult(false, false, true, -1, "", "timeout");
            }
            return new ExecutionResult(false, false, false, exitCodeOrMinusOne(), "", diagnosticsOr("I/O failure: " + io.getMessage()));
        } finally {
            killer.cancel(false);
            scheduler.shutdownNow();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        destroyForcibly();
    }

    private void pumpStderr() {
        try (InputStream err = process.getErrorStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = err.read(buffer)) != -1) {
                if (total < LIMIT_BYTES) {
                    int allowed = Math.min(read, LIMIT_BYTES - total);
                    stderrCaptured.write(buffer, 0, allowed);
                    total += allowed;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String diagnosticsOr(String fallback) {
        String diag = stderrOutput();
        if (diag.isBlank()) {
            return fallback;
        }
        return fallback + ": " + diag;
    }

    private String stderrOutput() {
        return new String(stderrCaptured.toByteArray(), StandardCharsets.UTF_8);
    }

    private int exitCodeOrMinusOne() {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return -1;
        }
    }

    private void destroyForcibly() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            out.close();
        } catch (Exception ignored) {
        }

        try {
            in.close();
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

        joinPump();
    }

    private void joinPump() {
        try {
            stderrPump.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) {
            throw new IOException("negative string length");
        }
        if (len == 0) {
            return "";
        }

        byte[] bytes = in.readNBytes(len);
        if (bytes.length != len) {
            throw new EOFException("truncated string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
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