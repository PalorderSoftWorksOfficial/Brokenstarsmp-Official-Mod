package com.palordersoftworks.brokenstarsmpmod.messages;

import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.text.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("brokenstarsmp").resolve("messages.yml");
    private static final Map<String, List<String>> MESSAGES = new LinkedHashMap<>();
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));
    private static boolean initialized;

    private Messages() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            Files.createDirectories(FILE.getParent());
            if (Files.notExists(FILE)) {
                try (InputStream input = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                    if (input == null) {
                        throw new IOException("messages.yml is missing from the mod jar");
                    }
                    Files.copy(input, FILE);
                }
            }
            reload();
            initialized = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize BrokenstarSMP messages", exception);
        }
    }

    public static synchronized void reload() throws IOException {
        if (!Files.exists(FILE)) {
            initialized = false;
            initialize();
            return;
        }

        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Object parsed = YAML.load(reader);
            if (!(parsed instanceof Map<?, ?> root)) {
                throw new IOException("messages.yml must contain a YAML mapping");
            }

            Map<String, List<String>> loaded = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : root.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }

                Object value = entry.getValue();
                if (value instanceof List<?> list) {
                    List<String> lines = new ArrayList<>();
                    for (Object line : list) {
                        if (line instanceof String string) {
                            lines.add(string);
                        }
                    }
                    loaded.put(key, List.copyOf(lines));
                } else if (value instanceof String string) {
                    loaded.put(key, List.of(string));
                }
            }

            MESSAGES.clear();
            MESSAGES.putAll(loaded);
        }
    }

    public static synchronized void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            YAML.dump(MESSAGES, writer);
        }
    }

    public static List<String> getLines(String key) {
        initialize();
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
