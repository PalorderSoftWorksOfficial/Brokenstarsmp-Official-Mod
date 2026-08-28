package com.palordersoftworks.brokenstarsmpmod.messages;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("brokenstarsmp").resolve("messages.json");
    private static final Map<String, List<String>> MESSAGES = new HashMap<>();

    private Messages() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(FILE.getParent());

            if (Files.notExists(FILE)) {
                try (InputStream input = Messages.class.getClassLoader().getResourceAsStream("messages.json")) {
                    if (input == null) {
                        throw new IOException("messages.json is missing from the mod jar");
                    }

                    try (OutputStream output = Files.newOutputStream(FILE)) {
                        input.transferTo(output);
                    }
                }
            }

            reload();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize BrokenstarSMP messages", exception);
        }
    }

    public static void reload() throws IOException {
        try (InputStream input = Files.newInputStream(FILE);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, List<String>> loaded = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    List<String> lines = new ArrayList<>();
                    value.getAsJsonArray().forEach(element -> lines.add(element.getAsString()));
                    loaded.put(entry.getKey(), List.copyOf(lines));
                } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    loaded.put(entry.getKey(), List.of(value.getAsString()));
                }
            }

            MESSAGES.clear();
            MESSAGES.putAll(loaded);
        }
    }

    public static List<String> getLines(String key) {
        return MESSAGES.getOrDefault(key, Collections.emptyList());
    }

    public static List<Component> renderLines(String key, Map<String, String> placeholders) {
        List<String> lines = getLines(key);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(MiniMessageApi.parse(line, placeholders));
        }

        return List.copyOf(components);
    }

    public static Component render(String key, Map<String, String> placeholders) {
        List<Component> lines = renderLines(key, placeholders);
        if (lines.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(index));
        }

        return result;
    }

    public static Path getFile() {
        return FILE;
    }
}