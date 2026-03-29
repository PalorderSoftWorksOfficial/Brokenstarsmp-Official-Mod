package com.palordersoftworks.economycraft.shop;

import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.ChatCompat;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
import net.minecraft.util.Formatting;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.LoreComponent;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;

public final class ShopUi {
    private ShopUi() {}

    private static final Formatting LABEL_PRIMARY_COLOR = Formatting.GOLD;
    private static final Formatting LABEL_SECONDARY_COLOR = Formatting.AQUA;
    private static final Formatting VALUE_COLOR = Formatting.DARK_PURPLE;
    private static final Formatting BALANCE_NAME_COLOR = Formatting.YELLOW;
    private static final Formatting BALANCE_LABEL_COLOR = Formatting.GOLD;
    private static final Formatting BALANCE_VALUE_COLOR = Formatting.DARK_PURPLE;

    public static void open(ServerPlayerEntity player, ShopManager shop) {
        Text title = Text.literal("Shop");

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new ShopMenu(id, inv, shop, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    static void openConfirm(ServerPlayerEntity player, ShopManager shop, ShopListing listing) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Confirm");
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new ConfirmMenu(id, inv, shop, listing, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openRemove(ServerPlayerEntity player, ShopManager shop, ShopListing listing) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Remove");
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new RemoveMenu(id, inv, shop, listing, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static Text createPriceLore(long price, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(price));
        if (tax > 0) {
            value.append(" (+").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return labeledValue("Price", value.toString(), LABEL_PRIMARY_COLOR);
    }

    private static ItemStack createBalanceItem(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = player.getGameProfile();
        ProfileComponentCompat.tryResolvedOrUnresolved(profile).ifPresent(resolvable ->
                head.set(net.minecraft.component.DataComponentTypes.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.getEntityWorld().getServer()).getBalance(player.getUuid(), true);
        head.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(IdentityCompat.of(player).name()).styled(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(net.minecraft.component.DataComponentTypes.LORE,
                new LoreComponent(List.of(balanceLore(balance))));
        return head;
    }

    private static Text balanceLore(long balance) {
        return Text.literal("Balance: ")
                .styled(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Text.literal(EconomyCraft.formatMoney(balance))
                        .styled(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    private static Text labeledValue(String label, String value, Formatting labelColor) {
        return Text.literal(label + ": ")
                .styled(s -> s.withItalic(false).withColor(labelColor))
                .append(Text.literal(value)
                        .styled(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }

    private static class ShopMenu extends ScreenHandler {
        private final ShopManager shop;
        private final ServerPlayerEntity viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleInventory container = new SimpleInventory(54);
        private int page;
        private final int navRowStart = 45;
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, PlayerInventory inv, ShopManager shop, ServerPlayerEntity viewer) {
            super(ScreenHandlerType.GENERIC_9X6, id);
            this.shop = shop;
            this.viewer = viewer;
            updatePage();
            shop.addListener(listener);
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
            listings = new ArrayList<>(shop.getListings());
            container.clear();
            int start = page * 45;
            int totalPages = (int) Math.ceil(listings.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;

                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName;
                ServerPlayerEntity sellerPlayer = viewer.getEntityWorld().getServer().getPlayerManager().getPlayer(l.seller);
                if (sellerPlayer != null) {
                    sellerName = IdentityCompat.of(sellerPlayer).name();
                } else {
                    sellerName = EconomyCraft.getManager(viewer.getEntityWorld().getServer()).getBestName(l.seller);
                }

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                        createPriceLore(l.price, tax),
                        labeledValue("Seller", sellerName, LABEL_SECONDARY_COLOR))));
                container.setStack(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }

            if (start + 45 < listings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }

            ItemStack balance = createBalanceItem(viewer);
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        if (listing.seller.equals(player.getUuid())) {
                            openRemove((ServerPlayerEntity) player, shop, listing);
                        } else {
                            ShopUi.openConfirm((ServerPlayerEntity) player, shop, listing);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < listings.size()) { page++; updatePage(); return; }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            shop.removeListener(listener);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }

    private static class ConfirmMenu extends ScreenHandler {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayerEntity viewer;
        private final SimpleInventory container = new SimpleInventory(9);

        ConfirmMenu(int id, PlayerInventory inv, ShopManager shop, ShopListing listing, ServerPlayerEntity viewer) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            container.setStack(2, confirm);

            String sellerName;
            ServerPlayerEntity sellerPlayer = viewer.getEntityWorld().getServer().getPlayerManager().getPlayer(listing.seller);
            if (sellerPlayer != null) {
                sellerName = IdentityCompat.of(sellerPlayer).name();
            } else {
                sellerName = EconomyCraft.getManager(viewer.getEntityWorld().getServer()).getBestName(listing.seller);
            }

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                    createPriceLore(listing.price, tax),
                    labeledValue("Seller", sellerName, LABEL_SECONDARY_COLOR))));
            container.setStack(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Cancel").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.DARK_RED)));
            container.setStack(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override
                    public boolean canTakeItems(PlayerEntity player) { return false; }
                });
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot == 2) {
                    ShopListing current = shop.getListing(listing.id);
                    ServerPlayerEntity sp = (ServerPlayerEntity) player;
                    var server = sp.getEntityWorld().getServer();

                    if (current == null) {
                        sp.sendMessage(Text.literal("Listing no longer available").formatted(Formatting.RED));
                    } else {
                        EconomyManager eco = EconomyCraft.getManager(server);
                        long cost = current.price;
                        long tax = Math.round(cost * EconomyConfig.get().taxRate);
                        long total = cost + tax;
                        long bal = eco.getBalance(player.getUuid(), true);

                        if (bal < total) {
                            sp.sendMessage(Text.literal("Not enough balance").formatted(Formatting.RED));
                        } else {
                            eco.removeMoney(player.getUuid(), total);
                            eco.addMoney(current.seller, cost);
                            ShopListing sold = shop.removeListing(current.id);
                            if (sold != null) {
                                shop.notifySellerSale(sold, sp);
                            }
                            ItemStack stack = current.item.copy();
                            int count = stack.getCount();
                            Text name = stack.getName();

                            String sellerName;
                            ServerPlayerEntity sellerPlayer = server.getPlayerManager().getPlayer(current.seller);
                            if (sellerPlayer != null) {
                                sellerName = IdentityCompat.of(sellerPlayer).name();
                            } else {
                                sellerName = eco.getBestName(current.seller);
                            }

                            if (!player.getInventory().insertStack(stack)) {
                                shop.addDelivery(player.getUuid(), stack);

                                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                if (ev != null) {
                                    Text msg = Text.literal("Item stored: ")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal("[Claim]")
                                                    .styled(s -> s.withUnderline(true)
                                                            .withColor(Formatting.GREEN)
                                                            .withClickEvent(ev)));
                                    sp.sendMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw(sp, "Item stored: ", "[Claim]", "/eco orders claim");
                                }
                            } else {
                                sp.sendMessage(
                                        Text.literal("Purchased " + count + "x " + name.getString() + " from " + sellerName +
                                                        " for " + EconomyCraft.formatMoney(total))
                                                .formatted(Formatting.GREEN)
                                );
                            }
                        }
                    }
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    ShopUi.open(sp, shop);
                    return;
                }

                if (slot == 6) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    ShopUi.open((ServerPlayerEntity) player, shop);
                    return;
                }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends ScreenHandler {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayerEntity viewer;
        private final SimpleInventory container = new SimpleInventory(9);

        RemoveMenu(int id, PlayerInventory inv, ShopManager shop, ShopListing listing, ServerPlayerEntity viewer) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            container.setStack(2, confirm);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                    createPriceLore(listing.price, tax),
                    labeledValue("Seller", "you", LABEL_SECONDARY_COLOR),
                    Text.literal("This will remove the listing").styled(s -> s.withItalic(false).withColor(Formatting.RED)))));
            container.setStack(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Cancel").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.DARK_RED)));
            container.setStack(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) { @Override public boolean canTakeItems(PlayerEntity p) { return false; }});
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot == 2) {
                    ShopListing removed = shop.removeListing(listing.id);
                    if (removed != null) {
                        ItemStack stack = removed.item.copy();
                        if (!player.getInventory().insertStack(stack)) {
                            shop.addDelivery(player.getUuid(), stack);

                            ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                            if (ev != null) {
                                Text msg = Text.literal("Item stored: ")
                                        .formatted(Formatting.YELLOW)
                                        .append(Text.literal("[Claim]")
                                                .styled(s -> s.withUnderline(true).withColor(Formatting.GREEN).withClickEvent(ev)));
                                ((ServerPlayerEntity) player).sendMessage(msg);
                            } else {
                                // Guaranteed clickable fallback
                                ChatCompat.sendRunCommandTellraw(
                                        (ServerPlayerEntity) player,
                                        "Item stored: ",
                                        "[Claim]",
                                        "/eco orders claim"
                                );
                            }
                        } else {
                            viewer.sendMessage(Text.literal("Listing removed"));
                        }
                    } else {
                        viewer.sendMessage(Text.literal("Listing no longer available"));
                    }
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    ShopUi.open((ServerPlayerEntity) player, shop);
                    return;
                }
                if (slot == 6) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    ShopUi.open((ServerPlayerEntity) player, shop);
                    return;
                }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int idx) { return ItemStack.EMPTY; }
    }
}
