package com.palordersoftworks.brokenstarsmpmod.config;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.config.Enums.RuleType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigManager {
    public static abstract class ConfigEntry<T> {
        protected T value;
        public final String key;
        public final Field field;
        public final RuleType type;
        public final Class<?> configClass;

        protected ConfigEntry(String key, Field field, RuleType type, T defaultValue, Class<?> configClass) {
            this.key = key;
            this.field = field;
            this.type = type;
            this.value = defaultValue;
            this.configClass = configClass;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
            try {
                if (field != null) {
                    field.set(null, value);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to update config field " + field.getName(), exception);
            }
        }
    }

    public static final class BoolConfig extends ConfigEntry<Boolean> {
        private BoolConfig(String key, Field field, Boolean defaultValue, Class<?> configClass) {
            super(key, field, RuleType.BOOL, defaultValue, configClass);
        }
    }

    public static final class IntConfig extends ConfigEntry<Integer> {
        private IntConfig(String key, Field field, Integer defaultValue, Class<?> configClass) {
            super(key, field, RuleType.INT, defaultValue, configClass);
        }
    }

    public static final class DoubleConfig extends ConfigEntry<Double> {
        private DoubleConfig(String key, Field field, Double defaultValue, Class<?> configClass) {
            super(key, field, RuleType.DOUBLE, defaultValue, configClass);
        }
    }

    public static final class StringConfig extends ConfigEntry<String> {
        private StringConfig(String key, Field field, String defaultValue, Class<?> configClass) {
            super(key, field, RuleType.STRING, defaultValue, configClass);
        }
    }

    public static final class EnumConfig extends ConfigEntry<String> {
        public final List<String> allowedOptions;

        private EnumConfig(String key, Field field, String defaultValue, List<String> allowedOptions, Class<?> configClass) {
            super(key, field, RuleType.ENUM, defaultValue, configClass);
            this.allowedOptions = allowedOptions;
        }

        @Override
        public void set(String value) {
            if (allowedOptions.isEmpty() || allowedOptions.contains(value)) {
                super.set(value);
            }
        }
    }

    public static final class TableConfig extends ConfigEntry<Map<String, Object>> {
        private TableConfig(String key, Field field, Map<String, Object> defaultValue, Class<?> configClass) {
            super(key, field, RuleType.TABLE, defaultValue, configClass);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final Map<String, ConfigEntry<?>> CONFIGS = new LinkedHashMap<>();
    private static final Map<Class<?>, Path> CONFIG_FILES = new LinkedHashMap<>();
    private static Path CONFIG_DIRECTORY;
    private static final Yaml YAML = createYaml();

    private ConfigManager() {
    }

    public static void setConfigDirectory(Path directory) {
        CONFIG_DIRECTORY = directory;
    }

    public static <T extends ConfigEntry<?>> T registerConfig(T entry) {
        CONFIGS.put(entry.key, entry);
        return entry;
    }

    public static void registerAnnotatedConfigs(Class<?> clazz) {
        Path configFile = CONFIG_DIRECTORY.resolve(toFileName(clazz));
        CONFIG_FILES.put(clazz, configFile);

        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Rule.class) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(null);
                Rule annotation = field.getAnnotation(Rule.class);
                String key = annotation.name().isEmpty() ? field.getName() : annotation.name();
                ConfigEntry<?> entry = createEntry(key, field, value, annotation, clazz);
                if (entry != null) {
                    registerConfig(entry);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to read config field " + field.getName(), exception);
            }
        }

        loadConfig(clazz);
        saveConfig(clazz);
    }

    public static void loadConfig(Class<?> clazz) {
        Path path = getConfigFile(clazz);
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> values)) {
                LOGGER.warn("Config file must contain a YAML mapping ({}); using defaults", path);
                return;
            }

            for (ConfigEntry<?> entry : CONFIGS.values()) {
                if (entry.configClass != clazz || !values.containsKey(entry.key)) {
                    continue;
                }
                applyValue(entry, convertValue(values.get(entry.key), entry.type));
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to load config {}; using defaults", path, exception);
        }
    }

    public static void saveConfig(Class<?> clazz) {
        Path path = getConfigFile(clazz);
        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> values = new LinkedHashMap<>();
            for (ConfigEntry<?> entry : CONFIGS.values()) {
                if (entry.configClass == clazz) {
                    values.put(entry.key, entry.get());
                }
            }

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                YAML.dump(values, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save config " + path, exception);
        }
    }

    public static void saveAll() {
        for (Class<?> clazz : CONFIG_FILES.keySet()) {
            saveConfig(clazz);
        }
    }

    public static Path getConfigFile(Class<?> clazz) {
        Path path = CONFIG_FILES.get(clazz);
        if (path == null) {
            throw new IllegalArgumentException("Config class is not registered: " + clazz.getName());
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    public static void registerCommands(Commands commands) {
        var root = Commands.literal("brokenstarsmp");

        root.executes(context -> {
            for (ConfigEntry<?> entry : CONFIGS.values()) {
                Rule annotation = entry.field.getAnnotation(Rule.class);
                String displayName = annotation.name().isEmpty() ? entry.key : annotation.name();
                String description = annotation.desc();

                if (entry instanceof BoolConfig boolConfig) {
                    context.getSource().getSender().sendMessage(buildInteractiveRow(boolConfig, displayName, List.of("true", "false"), description, displayName));
                } else if (entry instanceof EnumConfig enumConfig) {
                    context.getSource().getSender().sendMessage(buildInteractiveRow(enumConfig, displayName, enumConfig.allowedOptions, description, displayName));
                } else {
                    context.getSource().getSender().sendMessage(
                            Component.text(displayName + " = ").color(NamedTextColor.YELLOW)
                                    .append(Component.text(String.valueOf(entry.get())).color(NamedTextColor.GREEN))
                                    .append(Component.text(" | " + description).color(NamedTextColor.DARK_GRAY)));
                }
            }
            return Command.SINGLE_SUCCESS;
        });

        for (ConfigEntry<?> entry : CONFIGS.values()) {
            Rule annotation = entry.field.getAnnotation(Rule.class);
            String commandName = annotation.name().isEmpty() ? entry.key : annotation.name();

            switch (entry.type) {
                case BOOL -> root = root.then(Commands.literal(commandName)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean value = BoolArgumentType.getBool(context, "value");
                                    ((ConfigEntry<Boolean>) entry).set(value);
                                    saveConfig(entry.configClass);
                                    context.getSource().getSender().sendMessage(
                                            Component.text(commandName + " set to ").color(NamedTextColor.YELLOW)
                                                    .append(Component.text(String.valueOf(value)).color(NamedTextColor.GREEN)));
                                    return Command.SINGLE_SUCCESS;
                                })));
                case INT -> root = root.then(Commands.literal(commandName)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    ((ConfigEntry<Integer>) entry).set(value);
                                    saveConfig(entry.configClass);
                                    context.getSource().getSender().sendMessage(
                                            Component.text(commandName + " set to ").color(NamedTextColor.YELLOW)
                                                    .append(Component.text(String.valueOf(value)).color(NamedTextColor.GREEN)));
                                    return Command.SINGLE_SUCCESS;
                                })));
                case DOUBLE -> root = root.then(Commands.literal(commandName)
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> {
                                    double value = DoubleArgumentType.getDouble(context, "value");
                                    ((ConfigEntry<Double>) entry).set(value);
                                    saveConfig(entry.configClass);
                                    context.getSource().getSender().sendMessage(
                                            Component.text(commandName + " set to ").color(NamedTextColor.YELLOW)
                                                    .append(Component.text(String.valueOf(value)).color(NamedTextColor.GREEN)));
                                    return Command.SINGLE_SUCCESS;
                                })));
                case STRING, ENUM -> root = root.then(Commands.literal(commandName)
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(context -> {
                                    String value = StringArgumentType.getString(context, "value");
                                    ((ConfigEntry<String>) entry).set(value);
                                    saveConfig(entry.configClass);
                                    context.getSource().getSender().sendMessage(
                                            Component.text(commandName + " set to ").color(NamedTextColor.YELLOW)
                                                    .append(Component.text(value).color(NamedTextColor.GREEN)));
                                    return Command.SINGLE_SUCCESS;
                                })));
                case TABLE -> root = root.then(Commands.literal(commandName)
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(
                                    Component.text("Table configs must be modified programmatically").color(NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        }));
            }
        }

        commands.register(root.build());
    }

    private static ConfigEntry<?> createEntry(String key, Field field, Object value, Rule annotation, Class<?> clazz) {
        if (value instanceof Boolean booleanValue) {
            return new BoolConfig(key, field, booleanValue, clazz);
        }
        if (value instanceof Integer integerValue) {
            return new IntConfig(key, field, integerValue, clazz);
        }
        if (value instanceof Double doubleValue) {
            return new DoubleConfig(key, field, doubleValue, clazz);
        }
        if (value instanceof String stringValue) {
            if (annotation.options().length > 0) {
                return new EnumConfig(key, field, stringValue, List.of(annotation.options()), clazz);
            }
            return new StringConfig(key, field, stringValue, clazz);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> mapEntry : mapValue.entrySet()) {
                if (mapEntry.getKey() instanceof String stringKey) {
                    map.put(stringKey, mapEntry.getValue());
                }
            }
            return new TableConfig(key, field, map, clazz);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void applyValue(ConfigEntry<?> entry, Object value) {
        switch (entry.type) {
            case BOOL -> ((BoolConfig) entry).set((Boolean) value);
            case INT -> ((IntConfig) entry).set((Integer) value);
            case DOUBLE -> ((DoubleConfig) entry).set((Double) value);
            case STRING, ENUM -> ((ConfigEntry<String>) entry).set((String) value);
            case TABLE -> ((TableConfig) entry).set((Map<String, Object>) value);
        }
    }

    private static Object convertValue(Object value, RuleType type) {
        return switch (type) {
            case BOOL -> {
                if (!(value instanceof Boolean booleanValue)) {
                    throw new IllegalArgumentException("Expected boolean but got " + value);
                }
                yield booleanValue;
            }
            case INT -> {
                if (!(value instanceof Number number)) {
                    throw new IllegalArgumentException("Expected number but got " + value);
                }
                yield number.intValue();
            }
            case DOUBLE -> {
                if (!(value instanceof Number number)) {
                    throw new IllegalArgumentException("Expected number but got " + value);
                }
                yield number.doubleValue();
            }
            case STRING, ENUM -> {
                if (!(value instanceof String stringValue)) {
                    throw new IllegalArgumentException("Expected string but got " + value);
                }
                yield stringValue;
            }
            case TABLE -> {
                if (!(value instanceof Map<?, ?> mapValue)) {
                    throw new IllegalArgumentException("Expected mapping but got " + value);
                }
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> mapEntry : mapValue.entrySet()) {
                    if (mapEntry.getKey() instanceof String key) {
                        map.put(key, mapEntry.getValue());
                    }
                }
                yield map;
            }
        };
    }

    private static Component buildInteractiveRow(ConfigEntry<?> entry, String displayName, List<String> options, String description, String commandName) {
        Component row = Component.text("- " + displayName + " ").color(NamedTextColor.YELLOW)
                .append(Component.text(description).color(NamedTextColor.DARK_GRAY));

        row = row.append(Component.newline()).append(Component.text("Options: ").color(NamedTextColor.GRAY));

        for (String option : options) {
            boolean current = entry.get().toString().equalsIgnoreCase(option);
            NamedTextColor color = current ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            Component button = Component.text("[" + option + "]")
                    .color(color)
                    .clickEvent(ClickEvent.runCommand("/brokenstarsmp " + commandName + " " + option))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to set " + option)));
            row = row.append(Component.space()).append(button);
        }

        return row.append(Component.newline()).append(Component.text("Current value: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(entry.get())).color(NamedTextColor.AQUA));
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setWidth(120);
        return new Yaml(options);
    }

    private static String toFileName(Class<?> clazz) {
        if (clazz == ServerRules.class) {
            return "server-rules.yml";
        }
        if (clazz == UnstableSMPRules.class) {
            return "unstable-smp.yml";
        }

        String className = clazz.getSimpleName();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < className.length(); index++) {
            char character = className.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                result.append('-');
            }
            result.append(Character.toLowerCase(character));
        }
        return result + ".yml";
    }
}
