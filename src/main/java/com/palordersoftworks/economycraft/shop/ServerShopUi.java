package com.palordersoftworks.economycraft.shop;

import com.mojang.logging.LogUtils;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.PriceRegistry;
import com.palordersoftworks.economycraft.wand.SellWand;
import com.palordersoftworks.economycraft.util.ChatCompat;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
import net.minecraft.util.Formatting;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import com.palordersoftworks.economycraft.util.IdentifierCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ServerShopUi {
    private static final Text STORED_MSG = Text.literal("Item stored: ")
            .formatted(Formatting.YELLOW);
    private static final Map<String, IdentifierCompat.Id> CATEGORY_ICONS = buildCategoryIcons();
    private static final List<Integer> STAR_SLOT_ORDER = buildStarSlotOrder(5);
    private static final Formatting LABEL_PRIMARY_COLOR = Formatting.GOLD;
    private static final Formatting LABEL_SECONDARY_COLOR = Formatting.AQUA;
    private static final Formatting VALUE_COLOR = Formatting.DARK_PURPLE;
    private static final Formatting BALANCE_NAME_COLOR = Formatting.YELLOW;
    private static final Formatting BALANCE_LABEL_COLOR = Formatting.GOLD;
    private static final Formatting BALANCE_VALUE_COLOR = Formatting.DARK_PURPLE;

    private ServerShopUi() {}

    private static boolean isSellWandEntry(PriceRegistry.PriceEntry entry) {
        if (entry == null || entry.id() == null) return false;
        return "economycraft".equals(entry.id().namespace()) && "sell_wand".equals(entry.id().path());
    }

    public static void open(ServerPlayerEntity player, EconomyManager eco) {
        open(player, eco, null);
    }

    public static void open(ServerPlayerEntity player, EconomyManager eco, @Nullable String category) {
        if (category == null || category.isBlank()) {
            openRoot(player, eco);
            return;
        }

        PriceRegistry prices = eco.getPrices();
        String cat = category.trim();
        if (cat.contains(".")) {
            openItems(player, eco, cat);
            return;
        }

        List<String> subs = prices.buySubcategories(cat);
        if (!subs.isEmpty()) {
            openSubcategories(player, eco, cat);
            return;
        }

        openItems(player, eco, cat);
    }

    private static void openRoot(ServerPlayerEntity player, EconomyManager eco) {
        Text title = Text.literal("Shop");
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new CategoryMenu(id, inv, eco, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openSubcategories(ServerPlayerEntity player, EconomyManager eco, String topCategory) {
        Text title = Text.literal(formatCategoryTitle(topCategory));

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new SubcategoryMenu(id, inv, eco, topCategory, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openItems(ServerPlayerEntity player, EconomyManager eco, String category) {
        openItems(player, eco, category, null);
    }

    private static void openItems(ServerPlayerEntity player, EconomyManager eco, String category, @Nullable String displayTitle) {
        Text title;
        if (displayTitle != null) {
            title = Text.literal(formatCategoryTitle(displayTitle));
        } else {
            title = Text.literal(formatCategoryTitle(category));
        }

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new ItemMenu(id, inv, eco, category, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static String formatCategoryTitle(String category) {
        if (category == null || category.isBlank()) return "Shop";
        String[] parts = category.replace('.', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? category : sb.toString();
    }

    private static class CategoryMenu extends ScreenHandler {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayerEntity viewer;
        private List<String> categories = new ArrayList<>();
        private final SimpleInventory container;
        private final int itemsPerPage = 45;
        private final int navRowStart = 45;
        private final int[] slotToIndex = new int[54];
        private int page;

        CategoryMenu(int id, PlayerInventory inv, EconomyManager eco, ServerPlayerEntity viewer) {
            super(ScreenHandlerType.GENERIC_9X6, id);
            this.eco = eco;
            this.viewer = viewer;
            this.prices = eco.getPrices();

            refreshCategories();
            this.container = new SimpleInventory(54);
            setupSlots(inv);
            updatePage();
        }

        private void refreshCategories() {
            categories = new ArrayList<>();
            for (String cat : prices.buyTopCategories()) {
                if (hasItems(prices, cat)) categories.add(cat);
            }
        }

        private void setupSlots(PlayerInventory inv) {
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                });
            }
            int y = 18 + 6 * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clear();
            java.util.Arrays.fill(slotToIndex, -1);
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(categories.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= categories.size()) break;

                String cat = categories.get(idx);
                ItemStack icon = createCategoryIcon(cat, cat, prices, viewer);
                if (icon.isEmpty()) continue;

                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(formatCategoryTitle(cat)).styled(s -> s.withItalic(false).withColor(getCategoryColor(cat)).withBold(true)));
                icon.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Click to view items").styled(s -> s.withItalic(false)))));
                int slot = STAR_SLOT_ORDER.get(i);
                container.setStack(slot, icon);
                slotToIndex[slot] = idx;
            }

            fillEmptyWithPanes(container, itemsPerPage);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < categories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }

            ItemStack balance = createBalanceItem(viewer);
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = slotToIndex[slot];
                    if (index >= 0 && index < categories.size()) {
                        String cat = categories.get(index);
                        List<String> subs = prices.buySubcategories(cat);
                        if (subs.isEmpty()) {
                            openItems(viewer, eco, cat);
                        } else {
                            openSubcategories(viewer, eco, cat);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < categories.size()) { page++; updatePage(); return; }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }

    private static class SubcategoryMenu extends ScreenHandler {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayerEntity viewer;
        private final String topCategory;
        private List<String> subcategories = new ArrayList<>();
        private final SimpleInventory container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        SubcategoryMenu(int id, PlayerInventory inv, EconomyManager eco, String topCategory, ServerPlayerEntity viewer) {
            super(getMenuType(requiredRows(eco.getPrices().buySubcategories(topCategory).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.topCategory = topCategory;
            this.prices = eco.getPrices();
            refresh();
            this.rows = requiredRows(subcategories.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleInventory(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refresh() {
            subcategories = new ArrayList<>();
            for (String sub : prices.buySubcategories(topCategory)) {
                if (hasItems(prices, topCategory + "." + sub)) {
                    subcategories.add(sub);
                }
            }
        }

        private void setupSlots(PlayerInventory inv) {
            for (int i = 0; i < rows * 9; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                });
            }
            int y = 18 + rows * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clear();
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(subcategories.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= subcategories.size()) break;

                String sub = subcategories.get(idx);
                String full = topCategory + "." + sub;
                ItemStack icon = createCategoryIcon(sub, full, prices, viewer);
                if (icon.isEmpty()) continue;

                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(formatCategoryTitle(sub)).styled(s -> s.withItalic(false).withColor(Formatting.WHITE).withBold(true)));
                icon.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Click to view items").styled(s -> s.withItalic(false)))));
                container.setStack(i, icon);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < subcategories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back").styled(s -> s.withItalic(false).withColor(Formatting.DARK_RED).withBold(true)));
            container.setStack(navRowStart + 8, back);

            ItemStack balance = createBalanceItem(viewer);
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = page * itemsPerPage + slot;
                    if (index < subcategories.size()) {
                        String sub = subcategories.get(index);
                        openItems(viewer, eco, topCategory + "." + sub, sub);
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < subcategories.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 8) { openRoot(viewer, eco); return; }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }

    private static class ItemMenu extends ScreenHandler {
        private final EconomyManager eco;
        private final PriceRegistry prices;
        private final ServerPlayerEntity viewer;
        private final String category;
        private List<PriceRegistry.PriceEntry> entries = new ArrayList<>();
        private final SimpleInventory container;
        private final int rows;
        private final int itemsPerPage;
        private final int navRowStart;
        private int page;

        ItemMenu(int id, PlayerInventory inv, EconomyManager eco, String category, ServerPlayerEntity viewer) {
            super(getMenuType(requiredRows(eco.getPrices().buyableByCategory(category).size())), id);
            this.eco = eco;
            this.viewer = viewer;
            this.category = category;
            this.prices = eco.getPrices();

            refreshEntries();
            this.rows = requiredRows(entries.size());
            this.itemsPerPage = (rows - 1) * 9;
            this.navRowStart = itemsPerPage;
            this.container = new SimpleInventory(rows * 9);
            setupSlots(inv);
            updatePage();
        }

        private void refreshEntries() {
            entries = new ArrayList<>(prices.buyableByCategory(category));
        }

        private void setupSlots(PlayerInventory inv) {
            for (int i = 0; i < rows * 9; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                });
            }
            int y = 18 + rows * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clear();
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(entries.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;

                PriceRegistry.PriceEntry entry = entries.get(idx);
                ItemStack display = createDisplayStack(entry, viewer);
                if (display.isEmpty()) continue;

                int stackSize = Math.max(1, entry.stack());
                List<Text> lore = new ArrayList<>();
                if (entry.unitBuy() > 0) {
                    lore.add(labeledValue("Buy (Money)", EconomyCraft.formatMoney(entry.unitBuy()), LABEL_PRIMARY_COLOR));
                }
                if (entry.unitBuyShards() > 0) {
                    lore.add(labeledValue("Buy (Shards)", EconomyCraft.formatShards(entry.unitBuyShards()), Formatting.LIGHT_PURPLE));
                }

                Long stackPrice = safeMultiply(entry.unitBuy(), stackSize);
                Long stackShardPrice = safeMultiply(entry.unitBuyShards(), stackSize);
                if (stackSize > 1 && stackPrice != null && entry.unitBuy() > 0) {
                    lore.add(labeledValue("Stack Money (" + stackSize + ")", EconomyCraft.formatMoney(stackPrice), LABEL_PRIMARY_COLOR));
                }
                if (stackSize > 1 && stackShardPrice != null && entry.unitBuyShards() > 0) {
                    lore.add(labeledValue("Stack Shards (" + stackSize + ")", EconomyCraft.formatShards(stackShardPrice), Formatting.LIGHT_PURPLE));
                }

                boolean hasMoney = entry.unitBuy() > 0;
                boolean hasShards = entry.unitBuyShards() > 0;
                if (isSellWandEntry(entry)) {
                    lore.add(labeledValue("Buy", EconomyCraft.formatMoney(10_000L) + " + " + EconomyCraft.formatShards(100L), LABEL_SECONDARY_COLOR));
                    lore.add(labeledValue("Click", "Buy (requires both)", LABEL_SECONDARY_COLOR));
                } else if (hasMoney && hasShards) {
                    lore.add(labeledValue("Left click", "Buy 1 (money)", LABEL_SECONDARY_COLOR));
                    lore.add(labeledValue("Right click", "Buy 1 (shards)", LABEL_SECONDARY_COLOR));
                } else if (hasMoney) {
                    lore.add(labeledValue("Click", "Buy 1 (money)", LABEL_SECONDARY_COLOR));
                } else if (hasShards) {
                    lore.add(labeledValue("Click", "Buy 1 (shards)", LABEL_SECONDARY_COLOR));
                }
                if (stackSize > 1) {
                    lore.add(labeledValue("Shift-click", "Buy " + stackSize, LABEL_SECONDARY_COLOR));
                }

                display.set(DataComponentTypes.LORE, new LoreComponent(lore));
                display.setCount(1);
                container.setStack(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }

            if (start + itemsPerPage < entries.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back").styled(s -> s.withItalic(false).withColor(Formatting.DARK_RED).withBold(true)));
            container.setStack(navRowStart + 8, back);

            ItemStack balance = createBalanceItem(viewer);
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE) {
                if (slot < navRowStart) {
                    int index = page * itemsPerPage + slot;
                    if (index < entries.size()) {
                        handlePurchase(entries.get(index), type, dragType);
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < entries.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 8) {
                    if (category.contains(".")) {
                        String topCategory = category.substring(0, category.indexOf('.'));
                        openSubcategories(viewer, eco, topCategory);
                    } else {
                        openRoot(viewer, eco);
                    }
                    return;
                }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        private void handlePurchase(PriceRegistry.PriceEntry entry, SlotActionType clickType, int dragType) {
            if (entry.unitBuy() <= 0 && entry.unitBuyShards() <= 0) {
                viewer.sendMessage(Text.literal("This item cannot be purchased.")
                        .formatted(Formatting.RED));
                return;
            }

            ItemStack base = createDisplayStack(entry, viewer);
            if (base.isEmpty()) {
                viewer.sendMessage(Text.literal("Item unavailable.")
                        .formatted(Formatting.RED));
                return;
            }

            int stackSize = Math.max(1, entry.stack());
            int amount = clickType == SlotActionType.QUICK_MOVE ? stackSize : 1;

            if (isSellWandEntry(entry)) {
                if (!EconomyConfig.get().shardsEnabled) {
                    viewer.sendMessage(Text.literal("Shards are disabled.").formatted(Formatting.RED));
                    return;
                }
                if (amount != 1) {
                    viewer.sendMessage(Text.literal("Sell Wand can only be purchased one at a time.").formatted(Formatting.RED));
                    return;
                }
                long moneyCost = 10_000L;
                long shardCost = 100L;
                if (!eco.removeMoney(viewer.getUuid(), moneyCost)) {
                    viewer.sendMessage(Text.literal("Not enough balance.").formatted(Formatting.RED));
                    return;
                }
                if (!eco.removeShards(viewer.getUuid(), shardCost)) {
                    eco.addMoney(viewer.getUuid(), moneyCost);
                    viewer.sendMessage(Text.literal("Not enough shards.").formatted(Formatting.RED));
                    return;
                }
                boolean stored = giveToPlayer(createDisplayStack(entry, viewer), 1);
                viewer.sendMessage(Text.literal("Purchased Sell Wand for " + EconomyCraft.formatMoney(moneyCost) + " + " + EconomyCraft.formatShards(shardCost) + ".")
                        .formatted(Formatting.GREEN));
                if (stored) {
                    sendStoredMessage(viewer);
                }
                updatePage();
                return;
            }

            boolean useShards = dragType == 1;
            long unitPrice = useShards ? entry.unitBuyShards() : entry.unitBuy();
            if (unitPrice <= 0) {
                unitPrice = useShards ? entry.unitBuy() : entry.unitBuyShards();
                useShards = !useShards;
            }

            Long total = safeMultiply(unitPrice, amount);
            if (total == null) {
                viewer.sendMessage(Text.literal("Price too large.")
                        .formatted(Formatting.RED));
                return;
            }

            if (useShards) {
                long shards = eco.getShards(viewer.getUuid(), true);
                if (shards < total || !eco.removeShards(viewer.getUuid(), total)) {
                    viewer.sendMessage(Text.literal("Not enough shards.")
                            .formatted(Formatting.RED));
                    return;
                }
            } else {
                long balance = eco.getBalance(viewer.getUuid(), true);
                if (balance < total || !eco.removeMoney(viewer.getUuid(), total)) {
                    viewer.sendMessage(Text.literal("Not enough balance.")
                            .formatted(Formatting.RED));
                    return;
                }
            }

            boolean stored = giveToPlayer(base, amount);

            Text success = Text.literal(
                    "Purchased " + amount + "x " + base.getName().getString() +
                            " for " + (useShards ? EconomyCraft.formatShards(total) : EconomyCraft.formatMoney(total)))
                    .formatted(Formatting.GREEN);
            viewer.sendMessage(success);

            if (stored) {
                sendStoredMessage(viewer);
            }

            updatePage();
        }

        private boolean giveToPlayer(ItemStack base, int amount) {
            int remaining = amount;
            boolean stored = false;
            while (remaining > 0) {
                int give = Math.min(base.getMaxCount(), remaining);
                ItemStack stack = base.copyWithCount(give);
                if (!viewer.getInventory().insertStack(stack)) {
                    eco.getShop().addDelivery(viewer.getUuid(), stack);
                    stored = true;
                }
                remaining -= give;
            }
            return stored;
        }

        private void sendStoredMessage(ServerPlayerEntity player) {
            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
            if (ev != null) {
                player.sendMessage(STORED_MSG.copy()
                        .append(Text.literal("[Claim]")
                                .styled(s -> s.withUnderline(true)
                                        .withColor(Formatting.GREEN)
                                        .withClickEvent(ev))));
            } else {
                ChatCompat.sendRunCommandTellraw(player, "Item stored: ", "[Claim]", "/eco orders claim");
            }
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }

    private static ItemStack createCategoryIcon(String displayKey, String categoryKey, PriceRegistry prices, ServerPlayerEntity viewer) {
        IdentifierCompat.Id iconId = CATEGORY_ICONS.get(normalizeCategoryKey(displayKey));
        if (iconId == null && categoryKey != null) {
            iconId = CATEGORY_ICONS.get(normalizeCategoryKey(categoryKey));
        }

        if (iconId != null) {
            Optional<?> item = IdentifierCompat.registryGetOptional(Registries.ITEM, iconId);
            if (item.isPresent()) {
                Item resolved = resolveItemValue(item.get(), iconId, "category icon");
                if (resolved != null) {
                    return new ItemStack(resolved);
                }
            }
        }

        List<PriceRegistry.PriceEntry> entries = prices.buyableByCategory(categoryKey);
        if (entries.isEmpty() && categoryKey != null && !categoryKey.contains(".")) {
            for (String sub : prices.buySubcategories(categoryKey)) {
                List<PriceRegistry.PriceEntry> subEntries = prices.buyableByCategory(categoryKey + "." + sub);
                if (!subEntries.isEmpty()) {
                    entries = subEntries;
                    break;
                }
            }
        }

        if (!entries.isEmpty()) {
            ItemStack display = createDisplayStack(entries.get(0), viewer);
            if (!display.isEmpty()) {
                return display;
            }
        }

        return new ItemStack(Items.BOOK);
    }

    private static Map<String, IdentifierCompat.Id> buildCategoryIcons() {
        Map<String, IdentifierCompat.Id> map = new HashMap<>();
        map.put(normalizeCategoryKey("Redstone"), IdentifierCompat.withDefaultNamespace("redstone"));
        map.put(normalizeCategoryKey("Food"), IdentifierCompat.withDefaultNamespace("cooked_beef"));
        map.put(normalizeCategoryKey("Ores"), IdentifierCompat.withDefaultNamespace("iron_ingot"));
        map.put(normalizeCategoryKey("Blocks"), IdentifierCompat.withDefaultNamespace("grass_block"));
        map.put(normalizeCategoryKey("Stones"), IdentifierCompat.withDefaultNamespace("cobblestone"));
        map.put(normalizeCategoryKey("Bricks"), IdentifierCompat.withDefaultNamespace("bricks"));
        map.put(normalizeCategoryKey("Copper"), IdentifierCompat.withDefaultNamespace("copper_block"));
        map.put(normalizeCategoryKey("Earth"), IdentifierCompat.withDefaultNamespace("dirt"));
        map.put(normalizeCategoryKey("Sand"), IdentifierCompat.withDefaultNamespace("sand"));
        map.put(normalizeCategoryKey("Wood"), IdentifierCompat.withDefaultNamespace("oak_log"));
        map.put(normalizeCategoryKey("Drops"), IdentifierCompat.withDefaultNamespace("gunpowder"));
        map.put(normalizeCategoryKey("Utility"), IdentifierCompat.withDefaultNamespace("totem_of_undying"));
        map.put(normalizeCategoryKey("Transport"), IdentifierCompat.withDefaultNamespace("saddle"));
        map.put(normalizeCategoryKey("Light"), IdentifierCompat.withDefaultNamespace("lantern"));
        map.put(normalizeCategoryKey("Plants"), IdentifierCompat.withDefaultNamespace("wheat"));
        map.put(normalizeCategoryKey("Tools"), IdentifierCompat.withDefaultNamespace("diamond_pickaxe"));
        map.put(normalizeCategoryKey("Weapons"), IdentifierCompat.withDefaultNamespace("diamond_sword"));
        map.put(normalizeCategoryKey("Armor"), IdentifierCompat.withDefaultNamespace("diamond_chestplate"));
        map.put(normalizeCategoryKey("Enchantments"), IdentifierCompat.withDefaultNamespace("enchanted_book"));
        map.put(normalizeCategoryKey("Brewing"), IdentifierCompat.withDefaultNamespace("water_bottle"));
        map.put(normalizeCategoryKey("Ocean"), IdentifierCompat.withDefaultNamespace("tube_coral"));
        map.put(normalizeCategoryKey("Nether"), IdentifierCompat.withDefaultNamespace("netherrack"));
        map.put(normalizeCategoryKey("End"), IdentifierCompat.withDefaultNamespace("end_stone"));
        map.put(normalizeCategoryKey("Deep dark"), IdentifierCompat.withDefaultNamespace("sculk"));
        map.put(normalizeCategoryKey("Archaeology"), IdentifierCompat.withDefaultNamespace("brush"));
        map.put(normalizeCategoryKey("Ice"), IdentifierCompat.withDefaultNamespace("ice"));
        map.put(normalizeCategoryKey("Dyed"), IdentifierCompat.withDefaultNamespace("blue_dye"));
        map.put(normalizeCategoryKey("Discs"), IdentifierCompat.withDefaultNamespace("music_disc_strad"));
        map.put(normalizeCategoryKey("Economy"), IdentifierCompat.withDefaultNamespace("emerald"));
        return map;
    }

    private static String normalizeCategoryKey(String key) {
        if (key == null) return "";
        String cleaned = key.replace('.', ' ').replace('-', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return cleaned;
    }

    private static List<Integer> buildStarSlotOrder(int height) {
        int width = 9;
        int centerX = (width - 1) / 2;
        int centerY = (height - 1) / 2;
        List<int[]> entries = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                double dx = x - centerX;
                double dy = y - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                entries.add(new int[]{idx, (int) (dist * 1000), y, x});
            }
        }

        entries.sort(Comparator
                .comparingInt((int[] a) -> a[1])
                .thenComparingInt(a -> a[2])
                .thenComparingInt(a -> a[3]));

        List<Integer> order = new ArrayList<>(entries.size());
        for (int[] e : entries) order.add(e[0]);
        return order;
    }

    private static Formatting getCategoryColor(String key) {
        String norm = normalizeCategoryKey(key);
        return switch (norm) {
            case "redstone" -> Formatting.RED;
            case "food" -> Formatting.GOLD;
            case "ores" -> Formatting.WHITE;
            case "blocks" -> Formatting.DARK_GREEN;
            case "stones" -> Formatting.GRAY;
            case "bricks" -> Formatting.DARK_GRAY;
            case "copper" -> Formatting.AQUA;
            case "earth" -> Formatting.GREEN;
            case "sand" -> Formatting.YELLOW;
            case "wood" -> Formatting.DARK_GREEN;
            case "drops" -> Formatting.GRAY;
            case "utility" -> Formatting.LIGHT_PURPLE;
            case "transport" -> Formatting.BLUE;
            case "light" -> Formatting.YELLOW;
            case "plants" -> Formatting.GREEN;
            case "tools" -> Formatting.AQUA;
            case "weapons" -> Formatting.RED;
            case "armor" -> Formatting.BLUE;
            case "enchantments" -> Formatting.LIGHT_PURPLE;
            case "brewing" -> Formatting.DARK_AQUA;
            case "ocean" -> Formatting.DARK_AQUA;
            case "nether" -> Formatting.RED;
            case "end" -> Formatting.LIGHT_PURPLE;
            case "deep dark" -> Formatting.DARK_BLUE;
            case "deep_dark" -> Formatting.DARK_BLUE;
            case "archaeology" -> Formatting.GOLD;
            case "ice" -> Formatting.AQUA;
            case "dyed" -> Formatting.BLUE;
            case "discs" -> Formatting.DARK_PURPLE;
            case "economy" -> Formatting.GREEN;
            default -> Formatting.WHITE;
        };
    }

    private static boolean hasItems(PriceRegistry prices, String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) return false;
        if (!prices.buyableByCategory(categoryKey).isEmpty()) return true;
        if (!categoryKey.contains(".")) {
            for (String sub : prices.buySubcategories(categoryKey)) {
                if (!prices.buyableByCategory(categoryKey + "." + sub).isEmpty()) return true;
            }
        }
        return false;
    }

    private static void fillEmptyWithPanes(SimpleInventory container, int limit) {
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < limit && i < container.size(); i++) {
            if (container.getStack(i).isEmpty()) {
                container.setStack(i, filler.copy());
            }
        }
    }

    private static ScreenHandlerType<?> getMenuType(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    private static int requiredRows(int itemCount) {
        int contentRows = (int) Math.ceil(Math.max(1, itemCount) / 9.0);
        return Math.min(6, Math.max(2, contentRows + 1));
    }

    private static Text labeledValue(String label, String value, Formatting labelColor) {
        return Text.literal(label + ": ")
                .styled(s -> s.withItalic(false).withColor(labelColor))
                .append(Text.literal(value)
                        .styled(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }

    private static Text balanceLore(long balance) {
        return Text.literal("Balance: ")
                .styled(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Text.literal(EconomyCraft.formatMoney(balance))
                        .styled(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    private static ItemStack createBalanceItem(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ProfileComponentCompat.tryResolvedOrUnresolved(player.getGameProfile()).ifPresent(resolvable ->
                head.set(DataComponentTypes.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.getEntityWorld().getServer()).getBalance(player.getUuid(), true);
        String name = IdentityCompat.of(player).name();
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(balanceLore(balance))));
        return head;
    }

    private static List<Integer> buildStarSlotOrder() {
        int width = 9;
        int height = 5;
        int centerX = (width - 1) / 2;
        int centerY = (height - 1) / 2;
        List<int[]> entries = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                double dx = x - centerX;
                double dy = y - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                entries.add(new int[]{idx, (int) (dist * 1000), y, x});
            }
        }

        entries.sort(Comparator
                .comparingInt((int[] a) -> a[1])
                .thenComparingInt(a -> a[2])
                .thenComparingInt(a -> a[3]));

        List<Integer> order = new ArrayList<>(entries.size());
        for (int[] e : entries) order.add(e[0]);
        return order;
    }

    private static ItemStack createDisplayStack(PriceRegistry.PriceEntry entry, ServerPlayerEntity viewer) {
        try {
            IdentifierCompat.Id id = entry.id();
            if (id != null && "economycraft".equals(id.namespace()) && "sell_wand".equals(id.path())) {
                return SellWand.createSellWandItem();
            }

            Optional<?> item = IdentifierCompat.registryGetOptional(Registries.ITEM, id);
            if (item.isPresent()) {
                Item resolved = resolveItemValue(item.get(), id, "display stack");
                if (resolved != null && resolved != Items.AIR) {
                    return new ItemStack(resolved);
                }
                return ItemStack.EMPTY;
            }

            String path = id.path();
            if (path.startsWith("enchanted_book_")) {
                return createEnchantedBookStack(id, viewer);
            }

            return createPotionStack(id);
        } catch (RuntimeException ex) {
            LogUtils.getLogger().error("[EconomyCraft] Failed to create display stack for {}", entry.id().asString(), ex);
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack createEnchantedBookStack(IdentifierCompat.Id key, ServerPlayerEntity viewer) {
        String path = key.path();
        String suffix = path.substring("enchanted_book_".length());
        int lastUnderscore = suffix.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= suffix.length() - 1) return ItemStack.EMPTY;

        String enchantPath = suffix.substring(0, lastUnderscore);
        String levelStr = suffix.substring(lastUnderscore + 1);

        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            return ItemStack.EMPTY;
        }

        if (enchantPath.equals("curse_of_binding")) enchantPath = "binding_curse";
        else if (enchantPath.equals("curse_of_vanishing")) enchantPath = "vanishing_curse";

        IdentifierCompat.Id enchantId = IdentifierCompat.fromNamespaceAndPath(key.namespace(), enchantPath);
        if (enchantId == null) {
            return ItemStack.EMPTY;
        }
        RegistryKey<Enchantment> enchKey = IdentifierCompat.createResourceKey(RegistryKeys.ENCHANTMENT, enchantId);
        if (enchKey == null) {
            return ItemStack.EMPTY;
        }
        Optional<RegistryEntry.Reference<Enchantment>> enchOpt = viewer.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOptional(enchKey);
        if (enchOpt.isEmpty()) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.set(enchOpt.get(), level);
        stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        return stack;
    }

    private static ItemStack createPotionStack(IdentifierCompat.Id key) {
        String path = key.path();
        Item baseItem = Items.POTION;
        String working = path;

        if (path.startsWith("splash_")) {
            baseItem = Items.SPLASH_POTION;
            working = path.substring("splash_".length());
        } else if (path.startsWith("lingering_")) {
            baseItem = Items.LINGERING_POTION;
            working = path.substring("lingering_".length());
        } else if (path.startsWith("arrow_of_")) {
            baseItem = Items.TIPPED_ARROW;
            working = path.substring("arrow_of_".length());
        } else if (path.startsWith("potion_of_")) {
            working = path.substring("potion_of_".length());
        }

        if (working.endsWith("_splash_potion")) {
            baseItem = Items.SPLASH_POTION;
            working = working.substring(0, working.length() - "_splash_potion".length());
        } else if (working.endsWith("_lingering_potion")) {
            baseItem = Items.LINGERING_POTION;
            working = working.substring(0, working.length() - "_lingering_potion".length());
        } else if (working.endsWith("_potion")) {
            baseItem = Items.POTION;
            working = working.substring(0, working.length() - "_potion".length());
        }

        String potionPath;
        if (working.equals("water_bottle") || working.equals("water")) {
            potionPath = "water";
        } else {
            String effect = working;
            if (effect.endsWith("_extended")) {
                effect = effect.substring(0, effect.length() - "_extended".length());
                potionPath = "long_" + effect;
            } else if (effect.endsWith("_2")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = "strong_" + effect;
            } else if (effect.endsWith("_1")) {
                effect = effect.substring(0, effect.length() - 2);
                potionPath = effect;
            } else {
                potionPath = effect;
            }
        }

        if ("the_turtle_master".equals(potionPath)) {
            potionPath = "turtle_master";
        }

        IdentifierCompat.Id potionId = IdentifierCompat.fromNamespaceAndPath(key.namespace(), potionPath);
        if (potionId == null) {
            return ItemStack.EMPTY;
        }
        Optional<?> potion = IdentifierCompat.registryGetOptional(Registries.POTION, potionId);
        if (potion.isEmpty()) return ItemStack.EMPTY;

        RegistryEntry<Potion> potionEntry = resolvePotionEntry(potion.get(), potionId);
        if (potionEntry == null) {
            return ItemStack.EMPTY;
        }
        return PotionContentsComponent.createStack(baseItem, potionEntry);
    }

    @Nullable
    private static Object registryEntryStoredValue(RegistryEntry<?> entry) {
        var either = entry.getKeyOrValue();
        if (either.right().isPresent()) {
            return either.right().get();
        }
        return null;
    }

    private static Item resolveItemValue(Object value, IdentifierCompat.Id id, String context) {
        if (value instanceof Item resolved) {
            return resolved;
        }
        if (value instanceof RegistryEntry<?> entry) {
            Object inner = registryEntryStoredValue(entry);
            if (inner instanceof Item resolved) {
                return resolved;
            }
            return null;
        }
        return null;
    }

    @Nullable
    private static RegistryEntry<Potion> resolvePotionEntry(Object value, IdentifierCompat.Id id) {
        if (value instanceof Potion potion) {
            return Registries.POTION.getEntry(potion);
        }
        if (value instanceof RegistryEntry<?> entry) {
            Object inner = registryEntryStoredValue(entry);
            if (inner instanceof Potion) {
                @SuppressWarnings("unchecked")
                RegistryEntry<Potion> cast = (RegistryEntry<Potion>) entry;
                return cast;
            }
            return null;
        }
        return null;
    }

    private static Long safeMultiply(long a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
