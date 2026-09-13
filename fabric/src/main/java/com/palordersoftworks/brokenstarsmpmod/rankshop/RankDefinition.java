package com.palordersoftworks.brokenstarsmpmod.rankshop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One configured rank. Parsed once at startup from the {@code rankShopRanks} table;
 * immutable afterwards, so the server-side price/duration/group can never be
 * influenced by client input.
 *
 * Accepted keys inside each rank entry:
 * <pre>
 *   group: "vip"            # LuckPerms group, passed through exactly as configured
 *   price: 500              # Rank Tokens
 *   duration: "30d"         # h/d/w/m suffixes, or "permanent"
 *   renewal: "extend"       # extend | replace | deny
 *   replaces: "vip,vip2"    # comma separated groups removed on purchase (optional)
 *   enabled: true           # optional, default true
 *   sale_enabled: true      # optional per-rank sale flag
 *   sale_price: 350         # optional fixed sale price
 *   item:
 *     material: "GOLD_INGOT"
 *     slot: 11
 *     name: "&lt;gold&gt;..."            # MiniMessage
 *     lore_default: "..."       # MiniMessage, | separates lines
 *     lore_active: "..."
 *     lore_insufficient: "..."
 * </pre>
 */
public record RankDefinition(
        String id,
        String group,
        long price,
        Duration duration,
        RenewalMode renewal,
        List<String> replaces,
        boolean enabled,
        boolean saleEnabled,
        Long salePrice,
        int slot,
        Item material,
        String name,
        String loreDefault,
        String loreActive,
        String loreInsufficient
) {
    public enum RenewalMode { EXTEND, REPLACE, DENY }

    private static final Pattern DURATION = Pattern.compile("^(\\d+)\\s*([hdwm])$", Pattern.CASE_INSENSITIVE);

    public boolean isPermanent() {
        return duration == null;
    }

    /** Sale price for right now, or the base price when no sale applies. */
    public long priceFor(Instant now) {
        if (saleEnabled && RankShopConfig.RANK_SHOP_SALE_ENABLED && saleActive(now)) {
            if (salePrice != null && salePrice >= 0) {
                return salePrice;
            }
            double multiplier = RankShopConfig.RANK_SHOP_SALE_MULTIPLIER;
            if (multiplier > 0 && multiplier < 1.0) {
                return (long) Math.floor(price * multiplier);
            }
        }
        return price;
    }

    public boolean saleActive(Instant now) {
        if (!saleEnabled || !RankShopConfig.RANK_SHOP_SALE_ENABLED) {
            return false;
        }
        Instant starts = parseInstant(RankShopConfig.RANK_SHOP_SALE_STARTS, Instant.MIN);
        Instant ends = parseInstant(RankShopConfig.RANK_SHOP_SALE_ENDS, Instant.MAX);
        return !now.isBefore(starts) && !now.isAfter(ends);
    }

    /** Strict parser: throws on garbage so misconfiguration fails loudly at startup. */
    public static RankDefinition parse(String id, Map<String, Object> raw) {
        String group = str(raw.get("group"), null);
        if (group == null || group.isBlank()) {
            group = id;
        }
        long price = num(raw.get("price"), -1);
        if (price < 0) {
            throw new IllegalArgumentException("rank '" + id + "' needs a numeric 'price' >= 0");
        }

        Duration duration = parseDuration(str(raw.get("duration"), "permanent"));
        RenewalMode renewal = parseRenewal(str(raw.get("renewal"), "extend"));
        List<String> replaces = parseReplaces(raw.get("replaces"));
        boolean enabled = bool(raw.get("enabled"), true);
        boolean saleEnabled = bool(raw.get("sale_enabled"), false);
        Long salePrice = raw.containsKey("sale_price") ? num(raw.get("sale_price"), -1) : null;

        int slot = 10;
        Item material = Items.GOLD_INGOT;
        String name = "<gold><bold>" + id + "</bold></gold>";
        String loreDefault = "<gray>Price: <yellow><price> Rank Tokens</yellow>";
        String loreActive = "<green><bold>ACTIVE</bold>";
        String loreInsufficient = "<red>Not enough Rank Tokens";

        Object itemRaw = raw.get("item");
        if (itemRaw instanceof Map<?, ?> itemMap) {
            Map<String, Object> item = asStringMap(itemMap);
            slot = (int) num(item.get("slot"), 10);
            material = parseMaterial(str(item.get("material"), null));
            name = str(item.get("name"), name);
            loreDefault = str(item.get("lore_default"), loreDefault);
            loreActive = str(item.get("lore_active"), loreActive);
            loreInsufficient = str(item.get("lore_insufficient"), loreInsufficient);
        }

        return new RankDefinition(id, group, price, duration, renewal, replaces, enabled,
                saleEnabled, salePrice, slot, material, name, loreDefault, loreActive, loreInsufficient);
    }

    /** Parses every configured rank, skipping (and logging) invalid entries. */
    public static List<RankDefinition> loadAll(Map<String, Object> table, java.util.function.BiConsumer<String, Throwable> onError) {
        List<RankDefinition> ranks = new ArrayList<>();
        for (Map.Entry<String, Object> entry : table.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawMap)) {
                onError.accept(entry.getKey(), new IllegalArgumentException("rank must be a mapping"));
                continue;
            }
            try {
                ranks.add(parse(entry.getKey(), asStringMap(rawMap)));
            } catch (Exception e) {
                onError.accept(entry.getKey(), e);
            }
        }
        return ranks;
    }

    /** Strict duration parser: "1h", "12h", "1d", "7d", "30d", "365d", "permanent". */
    public static Duration parseDuration(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.equals("permanent") || trimmed.equals("perma") || trimmed.equals("forever")) {
            return null;
        }
        Matcher matcher = DURATION.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid duration '" + input + "' (use e.g. 1h, 30d, permanent)");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7);
            case "m" -> Duration.ofDays(amount * 30);
            default -> null;
        };
    }

    private static RenewalMode parseRenewal(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "extend" -> RenewalMode.EXTEND;
            case "replace" -> RenewalMode.REPLACE;
            case "deny" -> RenewalMode.DENY;
            default -> throw new IllegalArgumentException("invalid renewal mode '" + value + "' (extend|replace|deny)");
        };
    }

    private static List<String> parseReplaces(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value != null && !value.toString().isBlank()) {
                    result.add(value.toString());
                }
            }
        } else if (raw instanceof String csv) {
            for (String part : csv.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    private static Item parseMaterial(String name) {
        if (name == null || name.isBlank()) {
            return Items.GOLD_INGOT;
        }
        // Fail safely with a visible BARRIER instead of crashing on invalid configs.
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(name.toLowerCase(Locale.ROOT)))
                .orElse(Items.BARRIER);
    }

    static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid sale timestamp '" + value + "' (ISO 8601 expected)", e);
        }
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static long num(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }

    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return map;
    }
}
