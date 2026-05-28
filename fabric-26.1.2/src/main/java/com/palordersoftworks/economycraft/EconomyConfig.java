package com.palordersoftworks.economycraft;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/**
 * Runtime economy settings loaded from {@code run/config/}{@value #CONFIG_FOLDER_NAME}{@code /config.json}.
 */
public final class EconomyConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Subdirectory under the server {@code config/} folder for this mod's {@code config.json}, shop data, prices, etc.
     */
    public static final String CONFIG_FOLDER_NAME = "economycraft";

    private static final String LOG_PREFIX = "[PalorderEconomy]";
    private static final String DEFAULT_RESOURCE_PATH = "/assets/palordersoftworks/economycraft/config.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public long startingBalance;
    public long dailyAmount;
    public long dailySellLimit;
    public double taxRate;
    @SerializedName("pvp_balance_loss_percentage")
    public double pvpBalanceLossPercentage;
    @SerializedName("standalone_commands")
    public boolean standaloneCommands;
    @SerializedName("standalone_admin_commands")
    public boolean standaloneAdminCommands;
    @SerializedName("scoreboard_enabled")
    public boolean scoreboardEnabled;
    @SerializedName("server_shop_enabled")
    public boolean serverShopEnabled = true;

    @SerializedName("compact_money_display")
    public boolean compactMoneyDisplay;

    @SerializedName("player_vault_enabled")
    public boolean playerVaultEnabled = true;

    @SerializedName("player_vault_require_permission")
    public boolean playerVaultRequirePermission;

    /** Chest rows per vault (1 to 6). */
    @SerializedName("player_vault_rows")
    public int playerVaultRows = 6;

    /** Vaults available when LuckPerms meta is absent (or LP not installed). */
    @SerializedName("player_vault_default_amount")
    public int playerVaultDefaultAmount = 1;

    /** Hard cap on how many numbered vaults a player may open. */
    @SerializedName("player_vault_max_amount")
    public int playerVaultMaxAmount = 54;

    /**
     * LuckPerms meta key holding the max vault count (integer string), e.g. {@code brokenstarsmp.economy.playervault.amount}.
     */
    @SerializedName("player_vault_luckperms_meta_key")
    public String playerVaultLuckPermsMetaKey = "brokenstarsmp.economy.playervault.amount";

    // --- Optional top-level command aliases ------------------------------------

    /**
     * When {@link #standaloneCommands} is true, also register /money, /baltop, /leaderboard, /worth, etc.
     * ({@code /balance} and {@code /bal} are always registered when standalone is on.)
     */
    @SerializedName(value = "extra_standalone_aliases", alternate = {"donut_style_standalone_aliases"})
    public boolean extraStandaloneAliases = true;

    /** Secondary currency (shards). */
    @SerializedName("shards_enabled")
    public boolean shardsEnabled = true;

    @SerializedName("starting_shards")
    public long startingShards;

    /** Shards granted to the killer on player kill (0 to disable). */
    @SerializedName("shards_per_player_kill")
    public long shardsPerPlayerKill = 10L;

    /** Shards included with /eco daily (0 to disable). */
    @SerializedName("daily_shards")
    public long dailyShards;

    /** Money PvP coinflip: stake held until accept or timeout. */
    @SerializedName("coinflip_enabled")
    public boolean coinflipEnabled = true;

    @SerializedName("coinflip_tax_rate")
    public double coinflipTaxRate;

    @SerializedName("coinflip_timeout_seconds")
    public int coinflipTimeoutSeconds = 120;

    /** How many players /eco balance top and shard tops show. */
    @SerializedName("baltop_count")
    public int baltopCount = 10;

    private static EconomyConfig INSTANCE = new EconomyConfig();
    private static Path file;

    public static EconomyConfig get() {
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        Path dir = server != null
                ? server.getRunDirectory().resolve("config").resolve(CONFIG_FOLDER_NAME)
                : Path.of("config").resolve(CONFIG_FOLDER_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        file = dir.resolve("config.json");

        if (Files.notExists(file)) {
            copyDefaultFromJarOrThrow();
        } else {
            mergeNewDefaultsFromBundledDefault();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EconomyConfig parsed = GSON.fromJson(json, EconomyConfig.class);
            if (parsed == null) {
                throw new IllegalStateException("config.json parsed to null");
            }
            INSTANCE = parsed;
            INSTANCE.normalizeAfterLoad();
        } catch (Exception e) {
            throw new IllegalStateException(LOG_PREFIX + " Failed to read/parse config.json at " + file, e);
        }
    }

    private void normalizeAfterLoad() {
        if (playerVaultLuckPermsMetaKey == null || playerVaultLuckPermsMetaKey.isBlank()) {
            playerVaultLuckPermsMetaKey = "brokenstarsmp.economy.playervault.amount";
        }
        if (playerVaultRows < 1) {
            playerVaultRows = 1;
        } else if (playerVaultRows > 6) {
            playerVaultRows = 6;
        }
        if (playerVaultDefaultAmount < 0) {
            playerVaultDefaultAmount = 0;
        }
        if (playerVaultMaxAmount < 1) {
            playerVaultMaxAmount = 54;
        }
        if (startingShards < 0) {
            startingShards = 0;
        }
        if (shardsPerPlayerKill < 0) {
            shardsPerPlayerKill = 0;
        }
        if (dailyShards < 0) {
            dailyShards = 0;
        }
        if (coinflipTaxRate < 0) {
            coinflipTaxRate = 0;
        } else if (coinflipTaxRate > 1) {
            coinflipTaxRate = 1;
        }
        if (coinflipTimeoutSeconds < 10) {
            coinflipTimeoutSeconds = 10;
        } else if (coinflipTimeoutSeconds > 600) {
            coinflipTimeoutSeconds = 600;
        }
        if (baltopCount < 1) {
            baltopCount = 1;
        } else if (baltopCount > 50) {
            baltopCount = 50;
        }
    }

    public static void save() {
        if (file == null) {
            throw new IllegalStateException(LOG_PREFIX + " EconomyConfig not initialized. Call load() first.");
        }
        try {
            Files.writeString(
                    file,
                    GSON.toJson(INSTANCE),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException(LOG_PREFIX + " Failed to save config.json at " + file, e);
        }
    }

    private static void copyDefaultFromJarOrThrow() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        LOG_PREFIX + " Missing bundled default " + DEFAULT_RESOURCE_PATH
                                + " (did you forget to include it in resources?)"
                );
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("{} Created {} from bundled default {}", LOG_PREFIX, file, DEFAULT_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException(LOG_PREFIX + " Failed to create config.json at " + file, e);
        }
    }

    private static void mergeNewDefaultsFromBundledDefault() {
        JsonObject defaults = readBundledDefaultJson();
        if (defaults == null) {
            LOGGER.warn("{} No bundled defaults found; skipping config merge.", LOG_PREFIX);
            return;
        }

        JsonObject userRoot;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("{} config.json root is not an object, skipping merge.", LOG_PREFIX);
                return;
            }
            userRoot = parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException(LOG_PREFIX + " Failed to read/parse user config.json for merge at " + file, ex);
        }

        int[] added = new int[]{0};
        addMissingRecursive(userRoot, defaults, added);

        if (added[0] > 0) {
            try {
                Files.writeString(file, GSON.toJson(userRoot), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException(LOG_PREFIX + " Failed to write merged config.json at " + file, ex);
            }
        }
    }

    private static JsonObject readBundledDefaultJson() {
        try (InputStream in = EconomyConfig.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                return null;
            }

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return null;
            }

            return parsed.getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException(LOG_PREFIX + " Failed to read bundled default config.json from " + DEFAULT_RESOURCE_PATH, ex);
        }
    }

    private static void addMissingRecursive(JsonObject target, JsonObject defaults, int[] added) {
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            String key = e.getKey();
            JsonElement defVal = e.getValue();

            if (!target.has(key)) {
                target.add(key, defVal == null ? JsonNull.INSTANCE : defVal.deepCopy());
                added[0]++;
                continue;
            }

            JsonElement curVal = target.get(key);
            if (curVal != null && curVal.isJsonObject()
                    && defVal != null && defVal.isJsonObject()) {
                addMissingRecursive(curVal.getAsJsonObject(), defVal.getAsJsonObject(), added);
            }
        }
    }
}
