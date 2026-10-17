package com.palordersoftworks.brokenstarsmpmod.framework.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuditLog {
    private final Path file;
    private int retention;

    public AuditLog(Path directory, int retention) {
        this.file = directory.resolve("audit.log");
        this.retention = Math.max(100, retention);
        reload(this.retention);
    }

    public synchronized void reload(int newRetention) {
        retention = Math.max(100, newRetention);
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() > retention) {
                Files.write(file, lines.subList(lines.size() - retention, lines.size()), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void record(UUID actor, String actorName, String action, String target,
                                    Object previous, Object next, String source, String profile, String event) {
        String line = String.join("\t",
                Instant.now().toString(),
                actor == null ? "console" : actor.toString(),
                clean(actorName == null ? "console" : actorName),
                clean(action),
                clean(target),
                clean(previous),
                clean(next),
                clean(source),
                clean(profile),
                clean(event));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trim();
        } catch (IOException ignored) {
        }
    }

    public synchronized List<String> lines() {
        try {
            return Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8)) : List.of();
        } catch (IOException exception) {
            return List.of();
        }
    }

    public Path file() {
        return file;
    }

    private void trim() throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() > retention) {
            Files.write(file, lines.subList(lines.size() - retention, lines.size()), StandardCharsets.UTF_8);
        }
    }

    private static String clean(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        text = text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return text.length() > 256 ? text.substring(0, 256) : text;
    }
}
