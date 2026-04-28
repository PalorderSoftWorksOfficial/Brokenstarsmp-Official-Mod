package com.palordersoftworks.economycraft.util;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum Formatting implements StringIdentifiable {
    BLACK("BLACK", '0', 0, 0),
    DARK_BLUE("DARK_BLUE", '1', 1, 170),
    DARK_GREEN("DARK_GREEN", '2', 2, 43520),
    DARK_AQUA("DARK_AQUA", '3', 3, 43690),
    DARK_RED("DARK_RED", '4', 4, 11141120),
    DARK_PURPLE("DARK_PURPLE", '5', 5, 11141290),
    GOLD("GOLD", '6', 6, 16755200),
    GRAY("GRAY", '7', 7, 11184810),
    DARK_GRAY("DARK_GRAY", '8', 8, 5592405),
    BLUE("BLUE", '9', 9, 5592575),
    GREEN("GREEN", 'a', 10, 5635925),
    AQUA("AQUA", 'b', 11, 5636095),
    RED("RED", 'c', 12, 16733525),
    LIGHT_PURPLE("LIGHT_PURPLE", 'd', 13, 16733695),
    YELLOW("YELLOW", 'e', 14, 16777045),
    WHITE("WHITE", 'f', 15, 16777215),
    OBFUSCATED("OBFUSCATED", 'k', true),
    BOLD("BOLD", 'l', true),
    STRIKETHROUGH("STRIKETHROUGH", 'm', true),
    UNDERLINE("UNDERLINE", 'n', true),
    ITALIC("ITALIC", 'o', true),
    RESET("RESET", 'r', -1, (Integer)null);

    public static final Codec<Formatting> CODEC = StringIdentifiable.createCodec(Formatting::values);
    public static final Codec<Formatting> COLOR_CODEC = CODEC.validate((formatting) -> formatting.isModifier() ? DataResult.error(() -> "Formatting was not a valid color: " + String.valueOf(formatting)) : DataResult.success(formatting));
    public static final char FORMATTING_CODE_PREFIX = '§';
    private static final Map<String, Formatting> BY_NAME = (Map)Arrays.stream(values()).collect(Collectors.toMap((formatting) -> sanitize(formatting.name), (formatting) -> formatting));
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private final String name;
    private final char code;
    private final boolean modifier;
    private final String stringValue;
    private final int colorIndex;
    private final @Nullable Integer colorValue;

    private static String sanitize(String string) {
        return string.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private Formatting(final String string2, final @Nullable char c, final int j, final Integer integer) {
        this(string2, c, false, j, integer);
    }

    private Formatting(final String string2, final char c, final boolean bl) {
        this(string2, c, bl, -1, (Integer)null);
    }

    private Formatting(final String string2, final char c, final @Nullable boolean bl, final int j, final Integer integer) {
        this.name = string2;
        this.code = c;
        this.modifier = bl;
        this.colorIndex = j;
        this.colorValue = integer;
        this.stringValue = "§" + String.valueOf(c);
    }

    public char getCode() {
        return this.code;
    }

    public int getColorIndex() {
        return this.colorIndex;
    }

    public boolean isModifier() {
        return this.modifier;
    }

    public boolean isColor() {
        return !this.modifier && this != RESET;
    }

    public @Nullable Integer getColorValue() {
        return this.colorValue;
    }

    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String toString() {
        return this.stringValue;
    }

    @Contract("!null->!null;_->_")
    public static @Nullable String strip(@Nullable String string) {
        return string == null ? null : FORMATTING_CODE_PATTERN.matcher(string).replaceAll("");
    }

    public static @Nullable Formatting byName(@Nullable String string) {
        return string == null ? null : (Formatting)BY_NAME.get(sanitize(string));
    }

    public static @Nullable Formatting byColorIndex(int i) {
        if (i < 0) {
            return RESET;
        } else {
            for(Formatting formatting : values()) {
                if (formatting.getColorIndex() == i) {
                    return formatting;
                }
            }

            return null;
        }
    }

    public static @Nullable Formatting byCode(char c) {
        char d = Character.toLowerCase(c);

        for(Formatting formatting : values()) {
            if (formatting.code == d) {
                return formatting;
            }
        }

        return null;
    }

    public static Collection<String> getNames(boolean bl, boolean bl2) {
        List<String> list = Lists.newArrayList();

        for(Formatting formatting : values()) {
            if ((!formatting.isColor() || bl) && (!formatting.isModifier() || bl2)) {
                list.add(formatting.getName());
            }
        }

        return list;
    }

    public String asString() {
        return this.getName();
    }
}
