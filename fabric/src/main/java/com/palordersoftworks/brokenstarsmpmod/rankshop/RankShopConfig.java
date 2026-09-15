package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.Rule;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the Rank Tokens + Rank Shop system.
 *
 * Registered through {@link ConfigManager}, so every scalar rule is also tunable
 * live via the admin {@code /brokenstarsmp} tree. Ranks themselves are a nested
 * TABLE (see {@link RankDefinition#parse} for the accepted keys) so the whole shop
 * can be reshaped without touching code.
 */
public final class RankShopConfig {
    private RankShopConfig() {}

    @Rule(name = "rankShopEnabled", desc = "Enable the /rankshop GUI and token exchange")
    public static boolean RANK_SHOP_ENABLED = true;

    // ---- currency -----------------------------------------------------------

    @Rule(name = "rankTokenMoneyPerToken", desc = "Server money required for 1 Rank Token (EconomyCraft balance is long-backed)")
    public static double RANK_TOKEN_MONEY_PER_TOKEN = 100_000_000.0;

    @Rule(name = "rankTokenConversionEnabled", desc = "Allow money -> Rank Token exchange in the shop GUI")
    public static boolean RANK_TOKEN_CONVERSION_ENABLED = true;

    /** token count (string key) -> money cost. Keys must be quoted strings in YAML. */
    @Rule(name = "rankShopConvertOptions", desc = "Exchange buttons: quoted token amount -> money cost")
    public static Map<String, Object> RANK_SHOP_CONVERT_OPTIONS = buildConvertOptions();

    private static Map<String, Object> buildConvertOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("1", 100_000_000L);
        options.put("10", 1_000_000_000L);
        options.put("100", 10_000_000_000L);
        return options;
    }

    // ---- permissions --------------------------------------------------------

    @Rule(name = "rankShopAdminPermission", desc = "Permission node for /ranktokens give|take|set (op fallback)")
    public static String RANK_SHOP_ADMIN_PERMISSION = "brokenstarsmp.ranktokens.admin";

    // ---- GUI ----------------------------------------------------------------

    @Rule(name = "rankShopTitle", desc = "Shop GUI title (MiniMessage)")
    public static String RANK_SHOP_TITLE = "<dark_gray><bold>Rank Shop</bold></dark_gray>";

    @Rule(name = "rankShopSize", desc = "Shop GUI size in slots (multiple of 9, max 54)")
    public static int RANK_SHOP_SIZE = 27;

    @Rule(name = "rankShopFiller", desc = "Filler material for empty shop slots")
    public static String RANK_SHOP_FILLER = "GRAY_STAINED_GLASS_PANE";

    @Rule(name = "rankShopExchangeSlot", desc = "Slot of the Rank Token Exchange button")
    public static int RANK_SHOP_EXCHANGE_SLOT = 22;

    @Rule(name = "rankShopBalanceSlot", desc = "Slot of the balance item")
    public static int RANK_SHOP_BALANCE_SLOT = 19;

    @Rule(name = "rankShopCloseSlot", desc = "Slot of the close button")
    public static int RANK_SHOP_CLOSE_SLOT = 25;

    @Rule(name = "rankShopBalanceItem", desc = "Material of the balance item")
    public static String RANK_SHOP_BALANCE_ITEM = "GOLD_NUGGET";

    @Rule(name = "rankShopBalanceName", desc = "Balance item name (MiniMessage, <tokens> placeholder)")
    public static String RANK_SHOP_BALANCE_NAME = "<yellow><bold>Your Rank Tokens</bold></yellow>";

    @Rule(name = "rankShopBalanceLore", desc = "Balance item lore (MiniMessage, <tokens> placeholder). | separates lines")
    public static String RANK_SHOP_BALANCE_LORE = "<gray>Balance: <yellow><tokens></yellow>|<gray>Exchange money at the gold ingot.";

    @Rule(name = "rankShopExchangeMaterial", desc = "Material of the exchange button")
    public static String RANK_SHOP_EXCHANGE_MATERIAL = "GOLD_INGOT";

    @Rule(name = "rankShopExchangeName", desc = "Exchange button name (MiniMessage)")
    public static String RANK_SHOP_EXCHANGE_NAME = "<gold><bold>Rank Token Exchange</bold></gold>";

    @Rule(name = "rankShopExchangeLore", desc = "Exchange button lore (MiniMessage, <rate> placeholder). | separates lines")
    public static String RANK_SHOP_EXCHANGE_LORE = "<gray>Convert server money|<gray>into Rank Tokens.||<yellow><rate></yellow> <gray>→ 1 Rank Token||<green>Click to exchange";

    @Rule(name = "rankShopCloseName", desc = "Close button name (MiniMessage)")
    public static String RANK_SHOP_CLOSE_NAME = "<red><bold>Close</bold></red>";

    // ---- rank definitions ---------------------------------------------------

    /**
     * rank id -> definition. Accepted keys per rank are documented in
     * {@link RankDefinition#parse}. Loaded once at startup via
     * {@link RankDefinition#loadAll}; edits require a restart (or /reload of configs).
     */
    @Rule(name = "rankShopRanks", desc = "Rank definitions; table configs are edited in rank-shop.yml")
    public static Map<String, Object> RANK_SHOP_RANKS = defaultRanks();

    private static Map<String, Object> defaultRanks() {
        Map<String, Object> ranks = new LinkedHashMap<>();
        ranks.put("vip", buildRank("vip", "GOLD_INGOT", 10, "<gold><bold>VIP</bold></gold>",
                500L, "30d", null));
        ranks.put("vipplus", buildRank("vipplus", "DIAMOND", 12, "<aqua><bold>VIP+</bold></aqua>",
                1000L, "30d", "vip"));
        ranks.put("mvp", buildRank("mvp", "EMERALD", 14, "<green><bold>MVP</bold></green>",
                2500L, "30d", "vip,vipplus"));
        return ranks;
    }

    private static Map<String, Object> buildRank(String group, String material, int slot, String name,
                                                 long price, String duration, String replacesCsv) {
        Map<String, Object> rank = new LinkedHashMap<>();
        rank.put("group", group);
        rank.put("price", price);
        rank.put("duration", duration);
        rank.put("renewal", "extend");
        if (replacesCsv != null) {
            rank.put("replaces", replacesCsv);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", material);
        item.put("slot", slot);
        item.put("name", name);
        item.put("lore_default", "<gray>Purchase the rank for your account.||<gray>Price: <yellow><price> Rank Tokens</yellow>|<gray>Duration: <yellow><duration></yellow>||<green>Click to purchase");
        item.put("lore_active", "<green><bold>ACTIVE</bold>||<gray>Remaining: <yellow><remaining></yellow>||<gray>Price: <yellow><price> Rank Tokens</yellow>||<green>Click to renew");
        item.put("lore_insufficient", "<gray>Price: <yellow><price> Rank Tokens</yellow>|<gray>Duration: <yellow><duration></yellow>||<red>Not enough Rank Tokens|<gray>Required: <yellow><price></yellow>|<gray>Balance: <yellow><balance></yellow>|<gray>Missing: <red><missing></red>");
        rank.put("item", item);
        return rank;
    }

    // ---- sales --------------------------------------------------------------

    @Rule(name = "rankShopSaleEnabled", desc = "Global sale multiplier active for the sale window")
    public static boolean RANK_SHOP_SALE_ENABLED = false;

    @Rule(name = "rankShopSaleMultiplier", desc = "Global sale price multiplier (0.75 = 25% off)")
    public static double RANK_SHOP_SALE_MULTIPLIER = 1.0;

    @Rule(name = "rankShopSaleStarts", desc = "Sale start (ISO instant, e.g. 2026-09-01T00:00:00Z, empty = always)")
    public static String RANK_SHOP_SALE_STARTS = "";

    @Rule(name = "rankShopSaleEnds", desc = "Sale end (ISO instant, empty = never)")
    public static String RANK_SHOP_SALE_ENDS = "";

    @Rule(name = "rankShopSaleNameSuffix", desc = "Sale badge appended to rank item names (MiniMessage)")
    public static String RANK_SHOP_SALE_NAME_SUFFIX = " <red><bold>SALE</bold></red>";

    // ---- confirmation GUI ----------------------------------------------------

    @Rule(name = "rankShopConfirmTitle", desc = "Confirmation GUI title (MiniMessage)")
    public static String RANK_SHOP_CONFIRM_TITLE = "<dark_gray><bold>Purchase <rank></bold></dark_gray>";

    @Rule(name = "rankShopConfirmLore", desc = "Confirmation item lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_CONFIRM_LORE = "<gray>Rank: <rank>||<gray>Price: <yellow><price> Rank Tokens</yellow>|<gray>Your balance: <yellow><balance></yellow>|<gray>After purchase: <yellow><remaining></yellow>||<gray>Duration: <yellow><duration></yellow>";

    @Rule(name = "rankShopConfirmMaterial", desc = "Confirmation item material")
    public static String RANK_SHOP_CONFIRM_MATERIAL = "CHEST";

    @Rule(name = "rankShopConfirmYesSlot", desc = "Confirm button slot in the confirmation GUI")
    public static int RANK_SHOP_CONFIRM_YES_SLOT = 11;

    @Rule(name = "rankShopConfirmNoSlot", desc = "Cancel button slot in the confirmation GUI")
    public static int RANK_SHOP_CONFIRM_NO_SLOT = 15;

    @Rule(name = "rankShopConfirmYesName", desc = "Confirm button name (MiniMessage)")
    public static String RANK_SHOP_CONFIRM_YES_NAME = "<green><bold>CONFIRM</bold></green>";

    @Rule(name = "rankShopConfirmNoName", desc = "Cancel button name (MiniMessage)")
    public static String RANK_SHOP_CONFIRM_NO_NAME = "<red><bold>CANCEL</bold></red>";

    // ---- exchange GUI ---------------------------------------------------------

    @Rule(name = "rankShopExchangeTitle", desc = "Exchange GUI title (MiniMessage)")
    public static String RANK_SHOP_EXCHANGE_TITLE = "<dark_gray><bold>Rank Token Exchange</bold></dark_gray>";

    @Rule(name = "rankShopExchangeInfoLore", desc = "Exchange info item lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_EXCHANGE_INFO_LORE = "<gray>Your money: <yellow><money></yellow>||<gray>Your Rank Tokens: <yellow><tokens></yellow>||<gray>Rate: <yellow><rate></yellow> <gray>→ 1 Rank Token";

    @Rule(name = "rankShopExchangeOptionName", desc = "Exchange option button name (MiniMessage)")
    public static String RANK_SHOP_EXCHANGE_OPTION_NAME = "<yellow><tokens> Rank Token(s)</yellow>";

    @Rule(name = "rankShopExchangeOptionLore", desc = "Exchange option button lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_EXCHANGE_OPTION_LORE = "<gray>Costs <yellow><money></yellow>||<green>Click to exchange";

    @Rule(name = "rankShopExchangeBackSlot", desc = "Back button slot in the exchange GUI")
    public static int RANK_SHOP_EXCHANGE_BACK_SLOT = 22;

    // ---- sell exchange (tokens -> money) ---------------------------------------

    @Rule(name = "rankTokenSellEnabled", desc = "Allow Rank Token -> money exchange in the shop GUI")
    public static boolean RANK_TOKEN_SELL_ENABLED = true;

    @Rule(name = "rankTokenSellMultiplier", desc = "Sell payout as a fraction of the buy rate (0.25 = 25% of the buy price, i.e. 75% below)")
    public static double RANK_TOKEN_SELL_MULTIPLIER = 0.25;

    @Rule(name = "rankShopSellSlot", desc = "Slot of the Sell Tokens button in the exchange GUI")
    public static int RANK_SHOP_SELL_SLOT = 11;

    @Rule(name = "rankShopSellButtonName", desc = "Sell button name in the exchange GUI (MiniMessage, <rate> placeholder)")
    public static String RANK_SHOP_SELL_BUTTON_NAME = "<green><bold>Sell Rank Tokens</bold></green>";

    @Rule(name = "rankShopSellButtonLore", desc = "Sell button lore in the exchange GUI (MiniMessage, <rate> placeholder). | separates lines")
    public static String RANK_SHOP_SELL_BUTTON_LORE = "<gray>Sell Rank Tokens back|<gray>for <yellow><rate></yellow> <gray>each.||<green>Click to sell";

    @Rule(name = "rankShopSellTitle", desc = "Sell GUI title (MiniMessage)")
    public static String RANK_SHOP_SELL_TITLE = "<dark_gray><bold>Sell Rank Tokens</bold></dark_gray>";

    @Rule(name = "rankShopSellMaterial", desc = "Material of sell buttons")
    public static String RANK_SHOP_SELL_MATERIAL = "EMERALD";

    @Rule(name = "rankShopSellInfoName", desc = "Sell info item name (MiniMessage)")
    public static String RANK_SHOP_SELL_INFO_NAME = "<green><bold>Sell Rank Tokens</bold></green>";

    @Rule(name = "rankShopSellInfoLore", desc = "Sell info item lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_SELL_INFO_LORE = "<gray>Your money: <yellow><money></yellow>||<gray>Your Rank Tokens: <yellow><tokens></yellow>||<gray>Buy rate: <yellow><buy_rate></yellow> <gray>→ 1 token|<gray>Sell rate: <yellow><rate></yellow> <gray>← 1 token";

    @Rule(name = "rankShopSellOptionName", desc = "Sell option button name (MiniMessage)")
    public static String RANK_SHOP_SELL_OPTION_NAME = "<green><tokens> Rank Token(s)</green>";

    @Rule(name = "rankShopSellOptionLore", desc = "Sell option button lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_SELL_OPTION_LORE = "<gray>You receive <yellow><money></yellow>||<green>Click to sell";

    @Rule(name = "rankShopSellAllSlot", desc = "Sell All button slot in the sell GUI")
    public static int RANK_SHOP_SELL_ALL_SLOT = 20;

    @Rule(name = "rankShopSellAllName", desc = "Sell All button name (MiniMessage)")
    public static String RANK_SHOP_SELL_ALL_NAME = "<green><bold>Sell All</bold></green>";

    @Rule(name = "rankShopSellAllLore", desc = "Sell All button lore (MiniMessage). | separates lines")
    public static String RANK_SHOP_SELL_ALL_LORE = "<gray>Sell your whole balance of <yellow><tokens></yellow> token(s)||<gray>You receive <yellow><money></yellow>||<green>Click to sell";

    // ---- messages -------------------------------------------------------------

    @Rule(name = "rankShopMsgBalance", desc = "Chat message for /ranktokens balance (MiniMessage)")
    public static String RANK_SHOP_MSG_BALANCE = "<yellow><bold><tokens></bold> <gray>Rank Token(s)";

    @Rule(name = "rankShopMsgPurchased", desc = "Chat message after a successful purchase (MiniMessage)")
    public static String RANK_SHOP_MSG_PURCHASED = "<green>You now have <rank> <gray>for <yellow><duration></yellow><green>. Enjoy!";

    @Rule(name = "rankShopMsgRenewed", desc = "Chat message after renewing (MiniMessage)")
    public static String RANK_SHOP_MSG_RENEWED = "<green><rank> renewed! <gray>Expires in <yellow><remaining></yellow><green>.";

    @Rule(name = "rankShopMsgInsufficient", desc = "Chat message when tokens are insufficient (MiniMessage)")
    public static String RANK_SHOP_MSG_INSUFFICIENT = "<red>Not enough Rank Tokens! <gray>Required: <yellow><price></yellow><gray>, you have <yellow><balance></yellow><gray>, missing <red><missing></red><gray>.";

    @Rule(name = "rankShopMsgDenied", desc = "Chat message when renewal mode is deny (MiniMessage)")
    public static String RANK_SHOP_MSG_DENIED = "<red>You already own <rank> <gray>and it cannot be renewed right now.";

    @Rule(name = "rankShopMsgConvertSuccess", desc = "Chat message after exchanging money (MiniMessage)")
    public static String RANK_SHOP_MSG_CONVERT_SUCCESS = "<green>Exchanged <yellow><money></yellow> <green>for <yellow><tokens> Rank Token(s)</yellow><green>.";

    @Rule(name = "rankShopMsgConvertFailed", desc = "Chat message when money is insufficient (MiniMessage)")
    public static String RANK_SHOP_MSG_CONVERT_FAILED = "<red>Not enough money! <gray>Required: <yellow><money></yellow><gray>, you have <yellow><balance></yellow><gray>.";

    @Rule(name = "rankShopMsgSellSuccess", desc = "Chat message after selling tokens (MiniMessage)")
    public static String RANK_SHOP_MSG_SELL_SUCCESS = "<green>Sold <yellow><tokens> Rank Token(s)</yellow> <green>for <yellow><money></yellow><green>.";

    // ---- sounds -----------------------------------------------------------------

    @Rule(name = "rankShopSoundPurchase", desc = "Sound on successful purchase (registry name, empty = silent)")
    public static String RANK_SHOP_SOUND_PURCHASE = "ENTITY_VILLAGER_YES";

    @Rule(name = "rankShopSoundDeny", desc = "Sound on failed purchase/exchange (registry name)")
    public static String RANK_SHOP_SOUND_DENY = "ENTITY_VILLAGER_NO";

    @Rule(name = "rankShopSoundClick", desc = "Sound on GUI button clicks (registry name)")
    public static String RANK_SHOP_SOUND_CLICK = "UI_BUTTON_CLICK";

    @Rule(name = "rankShopSoundConvert", desc = "Sound on a successful exchange (registry name)")
    public static String RANK_SHOP_SOUND_CONVERT = "ENTITY_PLAYER_LEVELUP";

    @Rule(name = "rankShopSoundSell", desc = "Sound on a successful token sale (registry name)")
    public static String RANK_SHOP_SOUND_SELL = "ENTITY_EXPERIENCE_ORB_PICKUP";
}
