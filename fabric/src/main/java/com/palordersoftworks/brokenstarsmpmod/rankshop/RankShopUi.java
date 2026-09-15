package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.palordersoftworks.brokenstarsmpmod.messages.MiniMessageApi;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chest GUIs for the Rank Shop (shop, purchase confirmation, token exchange),
 * built on the sgui API already shipped by this mod.
 *
 * Everything player-visible comes from {@link RankShopConfig} / the rank table and
 * is rendered through MiniMessage with injection-safe placeholders; clicks only
 * ever reference a rank id or a configured exchange option, never a price.
 */
public final class RankShopUi {
    private RankShopUi() {}

    /** Slot layout for exchange option buttons (up to 8 options). */
    private static final int[] EXCHANGE_OPTION_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    // ---- main shop -----------------------------------------------------------

    public static void openShop(ServerPlayer player) {
        int size = clampSize(RankShopConfig.RANK_SHOP_SIZE);
        SimpleGui gui = new SimpleGui(menuTypeFor(size), player, false);
        gui.setTitle(mm(RankShopConfig.RANK_SHOP_TITLE, Map.of()));

        Item filler = item(RankShopConfig.RANK_SHOP_FILLER, Items.STAINED_GLASS_PANE.gray());
        for (int i = 0; i < size; i++) {
            gui.setSlot(i, new GuiElementBuilder(filler).build());
        }

        // Balance display
        long tokens = RankShopService.store().getBalance(player.getUUID());
        gui.setSlot(RankShopConfig.RANK_SHOP_BALANCE_SLOT, new GuiElementBuilder(
                        item(RankShopConfig.RANK_SHOP_BALANCE_ITEM, Items.GOLD_NUGGET))
                .setName(mm(RankShopConfig.RANK_SHOP_BALANCE_NAME, Map.of("tokens", String.valueOf(tokens))))
                .setLore(loreLines(RankShopConfig.RANK_SHOP_BALANCE_LORE, Map.of("tokens", String.valueOf(tokens))))
                .build());

        // Exchange button
        if (RankShopConfig.RANK_TOKEN_CONVERSION_ENABLED
                && validSlot(RankShopConfig.RANK_SHOP_EXCHANGE_SLOT, size)) {
            double perToken = RankShopConfig.RANK_TOKEN_MONEY_PER_TOKEN;
            String rate = perToken > 0 ? RankShopEconomy.formatMoney((long) Math.floor(perToken)) : "?";
            gui.setSlot(RankShopConfig.RANK_SHOP_EXCHANGE_SLOT, new GuiElementBuilder(
                            item(RankShopConfig.RANK_SHOP_EXCHANGE_MATERIAL, Items.GOLD_INGOT))
                    .setName(mm(RankShopConfig.RANK_SHOP_EXCHANGE_NAME, Map.of("rate", rate)))
                    .setLore(loreLines(RankShopConfig.RANK_SHOP_EXCHANGE_LORE, Map.of("rate", rate)))
                    .setCallback((index, clickType, input, g) -> {
                        RankShopService.playSound(player, RankShopConfig.RANK_SHOP_SOUND_CLICK);
                        openExchange(player);
                    })
                    .build());
        }

        // Close button
        if (validSlot(RankShopConfig.RANK_SHOP_CLOSE_SLOT, size)) {
            gui.setSlot(RankShopConfig.RANK_SHOP_CLOSE_SLOT, new GuiElementBuilder(Items.BARRIER)
                    .setName(mm(RankShopConfig.RANK_SHOP_CLOSE_NAME, Map.of()))
                    .setCallback((index, clickType, input, g) -> player.closeContainer())
                    .build());
        }

        // Rank items
        for (RankDefinition rank : RankShopService.ranks()) {
            if (!rank.enabled() || !validSlot(rank.slot(), size)) {
                continue;
            }
            gui.setSlot(rank.slot(), rankButton(player, rank, size));
        }

        gui.open();
    }

    private static GuiElementBuilder rankButton(ServerPlayer player, RankDefinition rank, int size) {
        Instant now = Instant.now();
        long price = rank.priceFor(now);
        long balance = RankShopService.store().getBalance(player.getUUID());
        RankShopService.RankState state = RankShopService.stateOf(player.getUUID(), rank, now);
        boolean sale = price < rank.price();
        boolean insufficient = (state == RankShopService.RankState.NOT_OWNED
                || state == RankShopService.RankState.EXPIRED) && balance < price;

        String loreRaw = state == RankShopService.RankState.ACTIVE_TEMPORARY
                ? rank.loreActive()
                : insufficient ? rank.loreInsufficient() : rank.loreDefault();

        Long expiration = RankShopService.expirationOf(player.getUUID(), rank);
        String remaining = expiration == null ? "-"
                : RankShopService.formatRemaining(expiration - now.toEpochMilli());

        Map<String, String> placeholders = Map.of(
                "rank", RankShopService.rankDisplayName(rank),
                "price", String.valueOf(price),
                "normal_price", String.valueOf(rank.price()),
                "sale_price", String.valueOf(price),
                "duration", RankShopService.formatDuration(rank),
                "balance", String.valueOf(balance),
                "remaining", remaining,
                "tokens", String.valueOf(balance),
                "missing", String.valueOf(Math.max(0, price - balance)));

        String name = sale
                ? rank.name() + RankShopConfig.RANK_SHOP_SALE_NAME_SUFFIX
                : rank.name();

        return new GuiElementBuilder(rank.material())
                .setName(mm(name, placeholders))
                .setLore(loreLines(loreRaw, placeholders))
                .glow(sale)
                .setCallback((index, clickType, input, g) -> {
                    RankShopService.playSound(player, RankShopConfig.RANK_SHOP_SOUND_CLICK);
                    openConfirm(player, rank);
                });
    }

    // ---- purchase confirmation -------------------------------------------------

    public static void openConfirm(ServerPlayer player, RankDefinition rank) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(mm(RankShopConfig.RANK_SHOP_CONFIRM_TITLE,
                Map.of("rank", RankShopService.rankDisplayName(rank))));

        Instant now = Instant.now();
        long price = rank.priceFor(now);
        long balance = RankShopService.store().getBalance(player.getUUID());
        long after = Math.max(0, balance - price);

        Map<String, String> placeholders = Map.of(
                "rank", RankShopService.rankDisplayName(rank),
                "price", String.valueOf(price),
                "balance", String.valueOf(balance),
                "remaining", String.valueOf(after),
                "duration", RankShopService.formatDuration(rank));

        Item material = item(RankShopConfig.RANK_SHOP_CONFIRM_MATERIAL, Items.CHEST);
        for (int i = 0; i < 27; i++) {
            gui.setSlot(i, new GuiElementBuilder(
                    item(RankShopConfig.RANK_SHOP_FILLER, Items.STAINED_GLASS_PANE.gray())).build());
        }
        gui.setSlot(13, new GuiElementBuilder(material)
                .setName(mm(rank.name(), placeholders))
                .setLore(loreLines(RankShopConfig.RANK_SHOP_CONFIRM_LORE, placeholders))
                .build());

        gui.setSlot(RankShopConfig.RANK_SHOP_CONFIRM_YES_SLOT, new GuiElementBuilder(Items.DYE.lime())
                .setName(mm(RankShopConfig.RANK_SHOP_CONFIRM_YES_NAME, placeholders))
                .setCallback((index, clickType, input, g) -> {
                    boolean purchased = RankShopService.purchase(player, rank);
                    if (purchased) {
                        openShop(player);
                    } else {
                        openConfirm(player, rank);
                    }
                })
                .build());

        gui.setSlot(RankShopConfig.RANK_SHOP_CONFIRM_NO_SLOT, new GuiElementBuilder(Items.DYE.red())
                .setName(mm(RankShopConfig.RANK_SHOP_CONFIRM_NO_NAME, placeholders))
                .setCallback((index, clickType, input, g) -> openShop(player))
                .build());

        gui.open();
    }

    // ---- token exchange ----------------------------------------------------------

    public static void openExchange(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(mm(RankShopConfig.RANK_SHOP_EXCHANGE_TITLE, Map.of()));

        for (int i = 0; i < 27; i++) {
            gui.setSlot(i, new GuiElementBuilder(
                    item(RankShopConfig.RANK_SHOP_FILLER, Items.STAINED_GLASS_PANE.gray())).build());
        }

        double perToken = RankShopConfig.RANK_TOKEN_MONEY_PER_TOKEN;
        String rate = perToken > 0 ? RankShopEconomy.formatMoney((long) Math.floor(perToken)) : "?";
        long money = RankShopEconomy.getMoney(player.level().getServer(), player.getUUID());
        long tokens = RankShopService.store().getBalance(player.getUUID());

        Map<String, String> infoPlaceholders = Map.of(
                "money", RankShopEconomy.formatMoney(money),
                "tokens", String.valueOf(tokens),
                "rate", rate);

        gui.setSlot(13, new GuiElementBuilder(Items.GOLD_NUGGET)
                .setName(mm(RankShopConfig.RANK_SHOP_EXCHANGE_NAME, infoPlaceholders))
                .setLore(loreLines(RankShopConfig.RANK_SHOP_EXCHANGE_INFO_LORE, infoPlaceholders))
                .build());

        // Sell Tokens button (tokens -> money at the discounted sell rate)
        if (RankShopConfig.RANK_TOKEN_SELL_ENABLED
                && validSlot(RankShopConfig.RANK_SHOP_SELL_SLOT, 27)) {
            String sellRate = RankShopEconomy.formatMoney(RankShopService.sellPayout(1));
            gui.setSlot(RankShopConfig.RANK_SHOP_SELL_SLOT, new GuiElementBuilder(
                            item(RankShopConfig.RANK_SHOP_SELL_MATERIAL, Items.EMERALD))
                    .setName(mm(RankShopConfig.RANK_SHOP_SELL_BUTTON_NAME, Map.of("rate", sellRate)))
                    .setLore(loreLines(RankShopConfig.RANK_SHOP_SELL_BUTTON_LORE, Map.of("rate", sellRate)))
                    .setCallback((index, clickType, input, g) -> {
                        RankShopService.playSound(player, RankShopConfig.RANK_SHOP_SOUND_CLICK);
                        openSell(player);
                    })
                    .build());
        }

        // Sorted numeric exchange options -> deterministic slot layout.
        Map<Long, Long> options = new TreeMap<>();
        for (Map.Entry<String, Object> entry : RankShopConfig.RANK_SHOP_CONVERT_OPTIONS.entrySet()) {
            try {
                long tokenCount = Long.parseLong(entry.getKey().trim());
                long cost = entry.getValue() instanceof Number number
                        ? number.longValue()
                        : (long) Math.floor(perToken * tokenCount);
                if (tokenCount > 0 && cost > 0) {
                    options.put(tokenCount, cost);
                }
            } catch (NumberFormatException ignored) {
                // Config error: skip silently; the yml is admin-owned.
            }
        }

        int slotIndex = 0;
        for (Map.Entry<Long, Long> entry : options.entrySet()) {
            if (slotIndex >= EXCHANGE_OPTION_SLOTS.length) {
                break;
            }
            long tokenCount = entry.getKey();
            long cost = entry.getValue();
            Map<String, String> placeholders = Map.of(
                    "tokens", String.valueOf(tokenCount),
                    "money", RankShopEconomy.formatMoney(cost),
                    "rate", rate);

            gui.setSlot(EXCHANGE_OPTION_SLOTS[slotIndex++], new GuiElementBuilder(Items.GOLD_INGOT)                    .setName(mm(RankShopConfig.RANK_SHOP_EXCHANGE_OPTION_NAME, placeholders))
                    .setLore(loreLines(RankShopConfig.RANK_SHOP_EXCHANGE_OPTION_LORE, placeholders))
                    .setCallback((index, clickType, input, g) -> {
                        if (RankShopService.convertMoneyToTokens(player, tokenCount)) {
                            openExchange(player);
                        } else {
                            openExchange(player);
                        }
                    })
                    .build());
        }

        gui.setSlot(RankShopConfig.RANK_SHOP_EXCHANGE_BACK_SLOT, new GuiElementBuilder(Items.BARRIER)
                .setName(mm("<red><bold>Back</bold></red>", Map.of()))
                .setCallback((index, clickType, input, g) -> openShop(player))
                .build());

        gui.open();
    }

    // ---- token sell (tokens -> money) ----------------------------------------------

    /** Same configured token counts as the buy exchange, paid out at the sell rate. */
    public static void openSell(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(mm(RankShopConfig.RANK_SHOP_SELL_TITLE, Map.of()));

        for (int i = 0; i < 27; i++) {
            gui.setSlot(i, new GuiElementBuilder(
                    item(RankShopConfig.RANK_SHOP_FILLER, Items.STAINED_GLASS_PANE.gray())).build());
        }

        long money = RankShopEconomy.getMoney(player.level().getServer(), player.getUUID());
        long tokens = RankShopService.store().getBalance(player.getUUID());
        double perToken = RankShopConfig.RANK_TOKEN_MONEY_PER_TOKEN;
        String buyRate = perToken > 0 ? RankShopEconomy.formatMoney((long) Math.floor(perToken)) : "?";
        String sellRate = RankShopEconomy.formatMoney(RankShopService.sellPayout(1));

        Map<String, String> infoPlaceholders = Map.of(
                "money", RankShopEconomy.formatMoney(money),
                "tokens", String.valueOf(tokens),
                "buy_rate", buyRate,
                "rate", sellRate);

        gui.setSlot(13, new GuiElementBuilder(item(RankShopConfig.RANK_SHOP_SELL_MATERIAL, Items.EMERALD))
                .setName(mm(RankShopConfig.RANK_SHOP_SELL_INFO_NAME, infoPlaceholders))
                .setLore(loreLines(RankShopConfig.RANK_SHOP_SELL_INFO_LORE, infoPlaceholders))
                .build());

        // Same token counts as buying, deterministic slots.
        Map<Long, Long> options = new TreeMap<>();
        for (Map.Entry<String, Object> entry : RankShopConfig.RANK_SHOP_CONVERT_OPTIONS.entrySet()) {
            try {
                long tokenCount = Long.parseLong(entry.getKey().trim());
                if (tokenCount > 0) {
                    options.put(tokenCount, RankShopService.sellPayout(tokenCount));
                }
            } catch (NumberFormatException ignored) {
                // Config error: skip silently; the yml is admin-owned.
            }
        }

        int slotIndex = 0;
        for (Map.Entry<Long, Long> entry : options.entrySet()) {
            if (slotIndex >= EXCHANGE_OPTION_SLOTS.length) {
                break;
            }
            long tokenCount = entry.getKey();
            long payout = entry.getValue();
            if (payout <= 0) {
                continue;
            }
            Map<String, String> placeholders = Map.of(
                    "tokens", String.valueOf(tokenCount),
                    "money", RankShopEconomy.formatMoney(payout),
                    "rate", sellRate);

            gui.setSlot(EXCHANGE_OPTION_SLOTS[slotIndex++], new GuiElementBuilder(
                            item(RankShopConfig.RANK_SHOP_SELL_MATERIAL, Items.EMERALD))
                    .setName(mm(RankShopConfig.RANK_SHOP_SELL_OPTION_NAME, placeholders))
                    .setLore(loreLines(RankShopConfig.RANK_SHOP_SELL_OPTION_LORE, placeholders))
                    .setCallback((index, clickType, input, g) -> {
                        RankShopService.playSound(player, RankShopConfig.RANK_SHOP_SOUND_CLICK);
                        if (RankShopService.convertTokensToMoney(player, tokenCount)) {
                            openSell(player);
                        } else {
                            openSell(player);
                        }
                    })
                    .build());
        }

        // Sell All
        if (validSlot(RankShopConfig.RANK_SHOP_SELL_ALL_SLOT, 27) && tokens > 0) {
            long payout = RankShopService.sellPayout(tokens);
            Map<String, String> placeholders = Map.of(
                    "tokens", String.valueOf(tokens),
                    "money", RankShopEconomy.formatMoney(payout));
            gui.setSlot(RankShopConfig.RANK_SHOP_SELL_ALL_SLOT, new GuiElementBuilder(Items.GOLD_INGOT)
                    .setName(mm(RankShopConfig.RANK_SHOP_SELL_ALL_NAME, placeholders))
                    .setLore(loreLines(RankShopConfig.RANK_SHOP_SELL_ALL_LORE, placeholders))
                    .setCallback((index, clickType, input, g) -> {
                        RankShopService.playSound(player, RankShopConfig.RANK_SHOP_SOUND_CLICK);
                        if (RankShopService.convertTokensToMoney(player, tokens)) {
                            openShop(player);
                        } else {
                            openSell(player);
                        }
                    })
                    .build());
        }

        gui.setSlot(RankShopConfig.RANK_SHOP_EXCHANGE_BACK_SLOT, new GuiElementBuilder(Items.BARRIER)
                .setName(mm("<red><bold>Back</bold></red>", Map.of()))
                .setCallback((index, clickType, input, g) -> openExchange(player))
                .build());

        gui.open();
    }

    // ---- helpers -----------------------------------------------------------------

    private static Component mm(String text, Map<String, String> placeholders) {
        return RankShopService.render(text, placeholders);
    }

    /** Splits {@code |-separated} lore (a blank segment renders as an empty line). */
    private static List<Component> loreLines(String text, Map<String, String> placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : text.split("\\|", -1)) {
            lines.add(mm(line, placeholders));
        }
        return lines;
    }

    private static Item item(String name, Item fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(Identifier.parse(name.trim().toLowerCase(Locale.ROOT)))
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean validSlot(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private static int clampSize(int size) {
        int clamped = Math.max(9, Math.min(54, size));
        return clamped - clamped % 9;
    }

    private static MenuType<?> menuTypeFor(int size) {
        return switch (size / 9) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
