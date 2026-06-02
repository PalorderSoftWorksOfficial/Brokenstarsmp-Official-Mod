package com.palordersoftworks.economycraft;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

public final class AuditLog {
    private AuditLog() {
    }

    public static void record(MinecraftServer server, String message) {
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, message + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public static List<String> tail(MinecraftServer server, int limit) {
        Path file = file(server);
        try {
            if (!Files.exists(file)) return Collections.emptyList();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.size() <= limit ? lines : lines.subList(lines.size() - limit, lines.size());
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    private static Path file(MinecraftServer server) {
        return server.getRunDirectory().resolve("config").resolve(EconomyConfig.CONFIG_FOLDER_NAME).resolve("data").resolve("economy.log");
    }
}