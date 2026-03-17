package com.palordersoftworks.brokenstarsmpmod.config;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import com.palordersoftworks.brokenstarsmpmod.config.Enums.RuleType;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class ConfigManager {

    public static abstract class ConfigEntry<T> {
        protected T value;
        public final String key;
        public final Field field;
        public final RuleType type;

        public ConfigEntry(String key, Field field, RuleType type, T defaultValue) {
            this.key = key;
            this.field = field;
            this.type = type;
            this.value = defaultValue;
        }

        public T get() { return value; }

        public void set(T value) {
            this.value = value;
            try { if(field != null) field.set(null, value); } catch (IllegalAccessException ignored) {}
        }
    }

    public static class BoolConfig extends ConfigEntry<Boolean> {
        public BoolConfig(String key, Boolean defaultValue) { super(key, null, RuleType.BOOL, defaultValue); }
        public BoolConfig(String key, Field field, Boolean defaultValue) { super(key, field, RuleType.BOOL, defaultValue); }
    }

    public static class IntConfig extends ConfigEntry<Integer> { public IntConfig(String key, Field field, Integer defaultValue) { super(key, field, RuleType.INT, defaultValue); } }
    public static class DoubleConfig extends ConfigEntry<Double> { public DoubleConfig(String key, Field field, Double defaultValue) { super(key, field, RuleType.DOUBLE, defaultValue); } }
    public static class StringConfig extends ConfigEntry<String> { public StringConfig(String key, Field field, String defaultValue) { super(key, field, RuleType.STRING, defaultValue); } }

    public static class EnumConfig extends ConfigEntry<String> {
        public final List<String> allowedOptions;
        public EnumConfig(String key, Field field, String defaultValue, List<String> allowedOptions) { super(key, field, RuleType.ENUM, defaultValue); this.allowedOptions = allowedOptions; }
        @Override public void set(String value) { if (allowedOptions.isEmpty() || allowedOptions.contains(value)) super.set(value); }
        public String next() {
            int i = allowedOptions.indexOf(value);
            if (i == -1 || allowedOptions.isEmpty()) return value;
            return allowedOptions.get((i + 1) % allowedOptions.size());
        }
    }

    public static class TableConfig extends ConfigEntry<Map<String, Object>> { public TableConfig(String key, Field field, Map<String, Object> defaultValue) { super(key, field, RuleType.TABLE, defaultValue); } }

    private static final Map<String, ConfigEntry<?>> CONFIGS = new LinkedHashMap<>();

    public static <T extends ConfigEntry<?>> T registerConfig(T entry) { CONFIGS.put(entry.key, entry); return entry; }

    public static void registerAnnotatedConfigs(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Rule.class)) continue;
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                Object value = field.get(null);
                ConfigEntry<?> entry = null;
                String key = field.getName();
                Rule annotation = field.getAnnotation(Rule.class);

                if (value instanceof Boolean b) entry = new BoolConfig(key, field, b);
                else if (value instanceof Integer i) entry = new IntConfig(key, field, i);
                else if (value instanceof Double d) entry = new DoubleConfig(key, field, d);
                else if (value instanceof String s) {
                    if (annotation.options().length > 0) entry = new EnumConfig(key, field, s, List.of(annotation.options()));
                    else entry = new StringConfig(key, field, s);
                } else if (value instanceof Map<?, ?> map) entry = new TableConfig(key, field, (Map<String, Object>) map);

                if (entry != null) registerConfig(entry);
            } catch (IllegalAccessException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var literal = CommandManager.literal("brokenstarsmp");

            literal.executes(ctx -> {
                for (ConfigEntry<?> entry : CONFIGS.values()) {
                    Rule annotation = null;
                    if (entry.field != null) annotation = entry.field.getAnnotation(Rule.class);
                    String desc = (annotation != null) ? annotation.desc() : "No description";

                    if (entry instanceof BoolConfig boolEntry) {
                        boolean val = boolEntry.get();
                        boolean next = !val;

                        Text line = Text.literal(entry.key + " = ").formatted(Formatting.YELLOW)
                                .append(Text.literal(String.valueOf(val))
                                        .formatted(val ? Formatting.GREEN : Formatting.RED));
                                        Text.literal(String.valueOf(val))
                                                .formatted(val ? Formatting.GREEN : Formatting.RED)
                                                .styled(s -> s
                                                        .withClickEvent(new ClickEvent.RunCommand("/brokenstarsmp " + entry.key + " " + next))
                                                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to toggle")))
                                                )
                                .append(Text.literal(" | " + desc).formatted(Formatting.GRAY));

                        ctx.getSource().sendFeedback(() -> line, false);
                    }

                    else if (entry instanceof EnumConfig enumEntry) {
                        String val = enumEntry.get();
                        String next = enumEntry.next();

                        Text line = Text.literal(entry.key + " = ").formatted(Formatting.YELLOW)
                                .append(Text.literal(val)
                                        .formatted(Formatting.AQUA));
                                        Text.literal(val)
                                                .formatted(Formatting.AQUA)
                                                .styled(s -> s
                                                        .withClickEvent(new ClickEvent.RunCommand("/brokenstarsmp " + entry.key + " " + next))
                                                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to cycle")))
                                                )
                                .append(Text.literal(" | " + desc).formatted(Formatting.GRAY));

                        ctx.getSource().sendFeedback(() -> line, false);
                    }

                    else {
                        Text line = Text.literal(entry.key + " = ").formatted(Formatting.YELLOW)
                                .append(Text.literal(String.valueOf(entry.get())).formatted(Formatting.GREEN))
                                .append(Text.literal(" | " + desc).formatted(Formatting.GRAY));

                        ctx.getSource().sendFeedback(() -> line, false);
                    }
                }
                return Command.SINGLE_SUCCESS;
            });

            for (ConfigEntry<?> entry : CONFIGS.values()) {
                switch (entry.type) {
                    case BOOL -> literal = literal.then(CommandManager.literal(entry.key)
                            .then(CommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean val = BoolArgumentType.getBool(ctx, "value");
                                        ((ConfigEntry<Boolean>) entry).set(val);
                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal(entry.key + " set to ").formatted(Formatting.YELLOW)
                                                                .append(Text.literal(String.valueOf(val)).formatted(Formatting.GREEN)),
                                                false);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    );

                    case INT -> literal = literal.then(CommandManager.literal(entry.key)
                            .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "value");
                                        ((ConfigEntry<Integer>) entry).set(val);
                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal(entry.key + " set to ").formatted(Formatting.YELLOW)
                                                                .append(Text.literal(String.valueOf(val)).formatted(Formatting.GREEN)),
                                                false);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    );

                    case DOUBLE -> literal = literal.then(CommandManager.literal(entry.key)
                            .then(CommandManager.argument("value", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> {
                                        double val = DoubleArgumentType.getDouble(ctx, "value");
                                        ((ConfigEntry<Double>) entry).set(val);
                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal(entry.key + " set to ").formatted(Formatting.YELLOW)
                                                                .append(Text.literal(String.valueOf(val)).formatted(Formatting.GREEN)),
                                                false);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    );

                    case STRING -> literal = literal.then(CommandManager.literal(entry.key)
                            .then(CommandManager.argument("value", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String val = StringArgumentType.getString(ctx, "value");
                                        ((ConfigEntry<String>) entry).set(val);
                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal(entry.key + " set to ").formatted(Formatting.YELLOW)
                                                                .append(Text.literal(val).formatted(Formatting.GREEN)),
                                                false);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    );

                    case ENUM -> literal = literal.then(CommandManager.literal(entry.key)
                            .then(CommandManager.argument("value", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String val = StringArgumentType.getString(ctx, "value");
                                        ((EnumConfig) entry).set(val);
                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal(entry.key + " set to ").formatted(Formatting.YELLOW)
                                                                .append(Text.literal(val).formatted(Formatting.GREEN)),
                                                false);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    );

                    case TABLE -> literal = literal.then(CommandManager.literal(entry.key)
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(() ->
                                                Text.literal("Table configs must be modified programmatically").formatted(Formatting.RED),
                                        false);
                                return Command.SINGLE_SUCCESS;
                            })
                    );
                }
            }

            dispatcher.register(literal);
        });
    }
}