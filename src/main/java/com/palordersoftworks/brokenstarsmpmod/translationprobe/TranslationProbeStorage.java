package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TranslationProbeStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Yaml YAML = createYaml();

    private TranslationProbeStorage() {}

    public static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("translation_probe_config.yml");
    }

    private static Path legacyConfigPath(MinecraftServer server) {
        return server.getServerDirectory().resolve("config").resolve("brokenstarsmp").resolve("translation_probe_config.json");
    }

    public static CheckHacksConfig load(MinecraftServer server) {
        Path path = configPath(server);
        Path legacy = legacyConfigPath(server);
        try {
            Files.createDirectories(path.getParent());

            if (Files.exists(path)) {
                CheckHacksConfig parsed = parseYaml(Files.readString(path, StandardCharsets.UTF_8));
                if (parsed != null) {
                    parsed.mergeMissingHacksFrom(CheckHacksConfig.createDefaultRegistry());
                    parsed.normalizeAfterLoad();
                    return parsed;
                }
            }

            if (Files.exists(legacy)) {
                CheckHacksConfig parsed = parseJson(Files.readString(legacy, StandardCharsets.UTF_8));
                parsed.mergeMissingHacksFrom(CheckHacksConfig.createDefaultRegistry());
                parsed.normalizeAfterLoad();
                save(server, parsed);
                Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[BrokenStarSMP/CheckHacks] Migrated legacy JSON config to {}", path.toAbsolutePath());
                return parsed;
            }

            CheckHacksConfig fresh = CheckHacksConfig.createDefaultRegistry();
            save(server, fresh);
            LOGGER.info("[BrokenStarSMP/CheckHacks] Created default registry at {}", path.toAbsolutePath());
            return fresh;
        } catch (Exception e) {
            LOGGER.error("[BrokenStarSMP/CheckHacks] Failed to load config; using in-memory defaults.", e);
            return CheckHacksConfig.createDefaultRegistry();
        }
    }

    public static void save(MinecraftServer server, CheckHacksConfig config) {
        Path path = configPath(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, YAML.dump(toYamlValue(GSON.toJsonTree(config))), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("[BrokenStarSMP/CheckHacks] Failed to save config at {}", path.toAbsolutePath(), e);
        }
    }

    private static CheckHacksConfig parseYaml(String content) {
        Object loaded = YAML.load(content);
        if (!(loaded instanceof Map<?, ?> map)) {
            return null;
        }
        JsonElement json = fromYamlValue(map);
        return GSON.fromJson(json, CheckHacksConfig.class);
    }

    private static CheckHacksConfig parseJson(String content) {
        return GSON.fromJson(content, CheckHacksConfig.class);
    }

    private static Object toYamlValue(JsonElement element) {
        if (element == null || element instanceof JsonNull) {
            return null;
        }
        if (element instanceof JsonObject object) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.put(entry.getKey(), toYamlValue(entry.getValue()));
            }
            return map;
        }
        if (element instanceof JsonArray array) {
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement value : array) {
                list.add(toYamlValue(value));
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return new BigDecimal(primitive.getAsString());
        }
        return primitive.getAsString();
    }

    private static JsonElement fromYamlValue(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), fromYamlValue(entry.getValue()));
            }
            return object;
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object item : list) {
                array.add(fromYamlValue(item));
            }
            return array;
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        options.setWidth(160);
        return new Yaml(options);
    }
}
