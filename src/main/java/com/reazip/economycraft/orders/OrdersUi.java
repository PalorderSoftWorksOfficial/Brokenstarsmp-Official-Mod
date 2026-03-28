package com.reazip.economycraft.orders;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileComponentCompat;
import net.minecraft.util.Formatting;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class OrdersUi {
    private OrdersUi() {}
    private static final Formatting LABEL_PRIMARY_COLOR = Formatting.GOLD;
    private static final Formatting LABEL_SECONDARY_COLOR = Formatting.AQUA;
    private static final Formatting VALUE_COLOR = Formatting.DARK_PURPLE;
    private static final Formatting BALANCE_NAME_COLOR = Formatting.YELLOW;
    private static final Formatting BALANCE_LABEL_COLOR = Formatting.GOLD;
    private static final Formatting BALANCE_VALUE_COLOR = Formatting.DARK_PURPLE;

    public static void open(ServerPlayerEntity player, EconomyManager eco) {
        Text title = Text.literal("Orders");
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                try {
                    return new RequestMenu(id, inv, eco.getOrders(), eco, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static Text createRewardLore(long reward, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(reward));
        if (tax > 0) {
            value.append(" (-").append(EconomyCraft.formatMoney(tax)).append(" tax)");
        }
        return labeledValue("Reward", value.toString(), LABEL_PRIMARY_COLOR);
    }

    private static ItemStack createBalanceItem(EconomyManager eco, UUID playerId, @Nullable ServerPlayerEntity player, @Nullable String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var profile = player != null
                ? ProfileComponentCompat.tryResolvedOrUnresolved(player.getGameProfile())
                : ProfileComponentCompat.tryUnresolved(name != null && !name.isBlank() ? name : playerId.toString());
        profile.ifPresent(resolvable -> head.set(DataComponentTypes.PROFILE, resolvable));
        long balance = eco.getBalance(playerId, true);
        String displayName = name != null ? name : playerId.toString();
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName).styled(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(balanceLore(balance))));
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

    public static void openClaims(ServerPlayerEntity player, EconomyManager eco) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() { return Text.literal("Deliveries"); }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                return new ClaimMenu(id, inv, eco, player.getUuid());
            }
        });
    }

    private static class RequestMenu extends ScreenHandler {
        private final OrderManager orders;
        private final EconomyManager eco;
        private final ServerPlayerEntity viewer;
        private List<OrderRequest> requests = new ArrayList<>();
        private final SimpleInventory container = new SimpleInventory(54);
        private int page;
        private final int navRowStart = 45;
        private final Runnable listener = this::updatePage;

        RequestMenu(int id, PlayerInventory inv, OrderManager orders, EconomyManager eco, ServerPlayerEntity viewer) {
            super(ScreenHandlerType.GENERIC_9X6, id);
            this.orders = orders;
            this.eco = eco;
            this.viewer = viewer;
            updatePage();
            orders.addListener(listener);
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
            requests = new ArrayList<>(orders.getRequests());
            container.clear();
            int start = page * 45;
            int totalPages = (int) Math.ceil(requests.size() / 45.0);

            var server = viewer.getEntityWorld().getServer();

            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= requests.size()) break;

                OrderRequest r = requests.get(index);
                ItemStack display = r.item.copy();

                String reqName;
                ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(r.requester);
                if (requesterPlayer != null) {
                    reqName = IdentityCompat.of(requesterPlayer).name();
                } else {
                    reqName = EconomyCraft.getManager(server).getBestName(r.requester);
                }

                long tax = Math.round(r.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.component.DataComponentTypes.LORE,
                        new net.minecraft.component.type.LoreComponent(List.of(
                                createRewardLore(r.price, tax),
                                labeledValue("Amount", String.valueOf(r.amount), LABEL_PRIMARY_COLOR),
                                labeledValue("Requester", reqName, LABEL_SECONDARY_COLOR)
                        )));
                display.setCount(1);
                container.setStack(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 2, prev);
            }

            if (start + 45 < requests.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 6, next);
            }

            ItemStack balance = createBalanceItem(eco, viewer.getUuid(), viewer, IdentityCompat.of(viewer).name());
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < requests.size()) {
                        OrderRequest req = requests.get(index);
                        if (req.requester.equals(player.getUuid())) {
                            openRemove((ServerPlayerEntity) player, req);
                        } else {
                            openConfirm((ServerPlayerEntity) player, req);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 2 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 6 && (page + 1) * 45 < requests.size()) { page++; updatePage(); return; }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        private boolean hasItems(ServerPlayerEntity player, ItemStack proto, int amount) {
            int total = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isOf(proto.getItem())) total += s.getCount();
            }
            return total >= amount;
        }

        private void removeItems(ServerPlayerEntity player, ItemStack proto, int amount) {
            int remaining = amount;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isOf(proto.getItem())) {
                    int take = Math.min(s.getCount(), remaining);
                    s.decrement(take);
                    remaining -= take;
                    if (remaining <= 0) return;
                }
            }
        }

        private void openConfirm(ServerPlayerEntity player, OrderRequest req) {
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() { return Text.literal("Confirm"); }

                @Override
                public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                    return new ConfirmMenu(id, inv, req, RequestMenu.this);
                }
            });
        }

        private void openRemove(ServerPlayerEntity player, OrderRequest req) {
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() { return Text.literal("Remove"); }

                @Override
                public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                    return new RemoveMenu(id, inv, req, RequestMenu.this);
                }
            });
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            orders.removeListener(listener);
        }

        @Override public ItemStack quickMove(PlayerEntity player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ConfirmMenu extends ScreenHandler {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleInventory container = new SimpleInventory(9);

        ConfirmMenu(int id, PlayerInventory inv, OrderRequest req, RequestMenu parent) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            container.setStack(2, confirm);

            ItemStack item = req.item.copy();
            var server = parent.viewer.getEntityWorld().getServer();

            String requesterName;
            ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(req.requester);
            if (requesterPlayer != null) {
                requesterName = IdentityCompat.of(requesterPlayer).name();
            } else {
                requesterName = EconomyCraft.getManager(server).getBestName(req.requester);
            }

            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.component.DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(List.of(
                            createRewardLore(req.price, tax),
                            labeledValue("Amount", String.valueOf(req.amount), LABEL_PRIMARY_COLOR),
                            labeledValue("Requester", requesterName, LABEL_SECONDARY_COLOR)
                    )));
            container.setStack(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Cancel").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.DARK_RED)));
            container.setStack(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override public boolean canTakeItems(PlayerEntity p) { return false; }
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
        public void onSlotClick(int slot, int drag, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot == 2) {
                    OrderRequest current = parent.orders.getRequest(request.id);
                    ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                    var server = serverPlayer.getEntityWorld().getServer();

                    if (current == null) {
                        serverPlayer.sendMessage(Text.literal("Request no longer available").formatted(Formatting.RED));
                    } else if (!parent.hasItems(serverPlayer, current.item, current.amount)) {
                        serverPlayer.sendMessage(Text.literal("Not enough items").formatted(Formatting.RED));
                    } else {
                        long cost = current.price;
                        long bal = parent.eco.getBalance(current.requester, true);
                        if (bal < cost) {
                            serverPlayer.sendMessage(Text.literal("Requester can't pay").formatted(Formatting.RED));
                        } else {
                            parent.removeItems(serverPlayer, current.item.copy(), current.amount);
                            long tax = Math.round(cost * EconomyConfig.get().taxRate);
                            parent.eco.removeMoney(current.requester, cost);
                            parent.eco.addMoney(player.getUuid(), cost - tax);
                            parent.orders.removeRequest(current.id);

                            int remaining = current.amount;
                            while (remaining > 0) {
                                int c = Math.min(current.item.getMaxCount(), remaining);
                                parent.orders.addDelivery(current.requester, new ItemStack(current.item.getItem(), c));
                                remaining -= c;
                            }

                            String requesterName;
                            ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(current.requester);
                            if (requesterPlayer != null) {
                                requesterName = IdentityCompat.of(requesterPlayer).name();
                            } else {
                                requesterName = parent.eco.getBestName(current.requester);
                            }

                            serverPlayer.sendMessage(
                                    Text.literal("Fulfilled request for " + current.amount + "x " +
                                                    current.item.getName().getString() + " (" + requesterName + ")" +
                                                    " and earned " + EconomyCraft.formatMoney(cost - tax))
                                            .formatted(Formatting.GREEN)
                            );

                            if (requesterPlayer != null) {
                                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                if (ev != null) {
                                    Text msg = Text.literal("Your request for " + current.amount + "x " +
                                                    current.item.getName().getString() +
                                                    " has been fulfilled: ")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal("[Claim]")
                                                    .styled(s -> s.withUnderline(true)
                                                            .withColor(Formatting.GREEN)
                                                            .withClickEvent(ev)));
                                    requesterPlayer.sendMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw(
                                            requesterPlayer,
                                            "Your request for " + current.amount + "x " + current.item.getName().getString() + " has been fulfilled: ",
                                            "[Claim]",
                                            "/eco orders claim"
                                    );
                                }
                            }

                            parent.requests.removeIf(r -> r.id == current.id);
                            parent.updatePage();
                        }
                    }
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    OrdersUi.open((ServerPlayerEntity) player, parent.eco);
                    return;
                }

                if (slot == 6) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    OrdersUi.open((ServerPlayerEntity) player, parent.eco);
                    return;
                }
            }
            super.onSlotClick(slot, drag, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int idx) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends ScreenHandler {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleInventory container = new SimpleInventory(9);

        RemoveMenu(int id, PlayerInventory inv, OrderRequest req, RequestMenu parent) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.request = req;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            container.setStack(2, confirm);

            ItemStack item = req.item.copy();
            long tax = Math.round(req.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                    createRewardLore(req.price, tax),
                    labeledValue("Amount", String.valueOf(req.amount), LABEL_PRIMARY_COLOR),
                    Text.literal("This will remove the request").styled(s -> s.withItalic(false).withColor(Formatting.RED)))));
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
        public void onSlotClick(int slot, int drag, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot == 2) {
                    OrderRequest removed = parent.orders.removeRequest(request.id);
                    if (removed != null) {
                        ((ServerPlayerEntity) player).sendMessage(Text.literal("Request removed").formatted(Formatting.GREEN));
                    } else {
                        ((ServerPlayerEntity) player).sendMessage(Text.literal("Request no longer available").formatted(Formatting.RED));
                    }
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    OrdersUi.open((ServerPlayerEntity) player, parent.eco);
                    return;
                }
                if (slot == 6) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    OrdersUi.open((ServerPlayerEntity) player, parent.eco);
                    return;
                }
            }
            super.onSlotClick(slot, drag, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int idx) { return ItemStack.EMPTY; }
    }

    private static class ClaimMenu extends ScreenHandler {
        private final EconomyManager eco;
        private final UUID owner;
        private final List<ItemStack> orderItems;
        private final List<ItemStack> shopItems;
        private final SimpleInventory container = new SimpleInventory(54);
        private final List<ItemStack> items = new ArrayList<>();
        private int page;
        private final int navRowStart = 45;

        ClaimMenu(int id, PlayerInventory inv, EconomyManager eco, UUID owner) {
            super(ScreenHandlerType.GENERIC_9X6, id);
            this.eco = eco;
            this.owner = owner;
            this.orderItems = eco.getOrders().getDeliveries(owner);
            this.shopItems = eco.getShop().getDeliveries(owner);
            updatePage();
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                int idx = i;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                    @Override public boolean canTakeItems(PlayerEntity player) { return idx < 45 && super.canTakeItems(player); }
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
            items.clear();
            items.addAll(orderItems);
            items.addAll(shopItems);
            container.clear();
            int start = page * 45;
            int totalPages = (int)Math.ceil(items.size() / 45.0);
            for (int i = 0; i < 45; i++) {
                int index = start + i;
                if (index >= items.size()) break;
                container.setStack(i, items.get(index));
            }
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 2, prev);
            }
            if (start + 45 < items.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 6, next);
            }
            String name = null;
            ServerPlayerEntity viewer = getViewer();
            if (viewer != null) {
                name = IdentityCompat.of(viewer).name();
            } else {
                name = eco.getBestName(owner);
            }
            ItemStack balance = createBalanceItem(eco, owner, viewer, name);
            container.setStack(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        private ServerPlayerEntity getViewer() {
            return eco.getServer().getPlayerManager().getPlayer(owner);
        }

        private void removeStack(ItemStack stack) {
            eco.getOrders().removeDelivery(owner, stack);
            eco.getShop().removeDelivery(owner, stack);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    Slot s = this.slots.get(slot);
                    if (s.hasStack()) {
                        ItemStack stack = s.getStack();
                        ItemStack copy = stack.copy();
                        if (player.getInventory().insertStack(copy)) {
                            removeStack(stack);
                            updatePage();
                        }
                    }
                    return;
                }
                if (slot == navRowStart + 2 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 6 && (page + 1) * 45 < items.size()) { page++; updatePage(); return; }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int idx) {
            Slot slot = this.slots.get(idx);
            if (!slot.hasStack()) return ItemStack.EMPTY;
            ItemStack stack = slot.getStack();
            ItemStack copy = stack.copy();
            if (idx < 45) {
                if (player.getInventory().insertStack(copy)) {
                    removeStack(stack);
                    updatePage();
                    return copy;
                }
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }
    }
}
