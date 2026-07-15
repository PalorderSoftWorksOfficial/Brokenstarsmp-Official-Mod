package com.palordersoftworks.economycraft.orders;

import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.ChatCompat;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrdersUi {
    private OrdersUi() {}

    private static final Formatting LABEL_PRIMARY_COLOR = Formatting.GOLD;
    private static final Formatting LABEL_SECONDARY_COLOR = Formatting.AQUA;
    private static final Formatting VALUE_COLOR = Formatting.DARK_PURPLE;
    private static final Formatting BALANCE_NAME_COLOR = Formatting.YELLOW;
    private static final Formatting BALANCE_LABEL_COLOR = Formatting.GOLD;
    private static final Formatting BALANCE_VALUE_COLOR = Formatting.DARK_PURPLE;

    public static void open(ServerPlayerEntity player, EconomyManager eco) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Orders");
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                return new RequestMenu(id, inv, eco.getOrders(), eco, player);
            }
        });
    }

    public static void openClaims(ServerPlayerEntity player, EconomyManager eco) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Deliveries");
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                return new ClaimMenu(id, inv, eco, player.getUuid());
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

    private static ItemStack createBalanceItem(EconomyManager eco, UUID playerId, ServerPlayerEntity player, String name) {
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
                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return false;
                    }

                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }
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

                OrderRequest request = requests.get(index);
                ItemStack display = request.item.copy();
                display.setCount(1);

                String requesterName;
                ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(request.requester);
                if (requesterPlayer != null) {
                    requesterName = IdentityCompat.of(requesterPlayer).name();
                } else {
                    requesterName = EconomyCraft.getManager(server).getBestName(request.requester);
                }

                long tax = Math.round(request.price * EconomyConfig.get().taxRate);
                display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        createRewardLore(request.price, tax),
                        labeledValue("Amount", String.valueOf(request.amount), LABEL_PRIMARY_COLOR),
                        labeledValue("Requester", requesterName, LABEL_SECONDARY_COLOR)
                )));
                container.setStack(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 2, prev);
            }

            if (start + 45 < requests.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 6, next);
            }

            String name = IdentityCompat.of(viewer).name();
            ItemStack balance = createBalanceItem(eco, viewer.getUuid(), viewer, name);
            container.setStack(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);
        }

        @Override
        public void onSlotClick(int slot, int drag, SlotActionType type, PlayerEntity player) {
            if (slot < 0 || slot >= this.slots.size()) {
                return;
            }
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < requests.size()) {
                        OrderRequest request = requests.get(index);
                        if (request.requester.equals(player.getUuid())) {
                            openRemove((ServerPlayerEntity) player, request);
                        } else {
                            openConfirm((ServerPlayerEntity) player, request);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 2 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 6 && (page + 1) * 45 < requests.size()) {
                    page++;
                    updatePage();
                    return;
                }
            }
            super.onSlotClick(slot, drag, type, player);
        }

        private void openConfirm(ServerPlayerEntity player, OrderRequest request) {
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("Confirm");
                }

                @Override
                public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                    return new ConfirmMenu(id, inv, request, RequestMenu.this);
                }
            });
        }

        private void openRemove(ServerPlayerEntity player, OrderRequest request) {
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("Remove");
                }

                @Override
                public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                    return new RemoveMenu(id, inv, request, RequestMenu.this);
                }
            });
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            orders.removeListener(listener);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int idx) {
            return ItemStack.EMPTY;
        }
    }

    private static class ConfirmMenu extends ScreenHandler {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleInventory container = new SimpleInventory(9);

        ConfirmMenu(int id, PlayerInventory inv, OrderRequest request, RequestMenu parent) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.request = request;
            this.parent = parent;

            int give = Math.min(OrderFulfillment.countHeld(parent.viewer, request.item), request.amount);
            long payout = OrderFulfillment.payoutFor(request, give);

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            confirm.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    labeledValue("Give", give + " of " + request.amount, LABEL_PRIMARY_COLOR),
                    labeledValue("Earn", EconomyCraft.formatMoney(payout), LABEL_PRIMARY_COLOR)
            )));
            container.setStack(2, confirm);

            ItemStack item = request.item.copy();
            item.setCount(1);
            var server = parent.viewer.getEntityWorld().getServer();
            String requesterName;
            ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(request.requester);
            if (requesterPlayer != null) {
                requesterName = IdentityCompat.of(requesterPlayer).name();
            } else {
                requesterName = EconomyCraft.getManager(server).getBestName(request.requester);
            }
            long tax = Math.round(request.price * EconomyConfig.get().taxRate);
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    createRewardLore(request.price, tax),
                    labeledValue("Amount", String.valueOf(request.amount), LABEL_PRIMARY_COLOR),
                    labeledValue("Requester", requesterName, LABEL_SECONDARY_COLOR)
            )));
            container.setStack(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.DARK_RED)));
            container.setStack(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return false;
                    }
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
            if (slot < 0 || slot >= this.slots.size()) {
                return;
            }
            if (type == SlotActionType.PICKUP) {
                if (slot == 2) {
                    OrderRequest current = parent.orders.getRequest(request.id);
                    ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                    var server = serverPlayer.getEntityWorld().getServer();

                    if (current == null) {
                        serverPlayer.sendMessage(Text.literal("Request no longer available").formatted(Formatting.RED));
                    } else {
                        int give = Math.min(OrderFulfillment.countHeld(serverPlayer, current.item), current.amount);
                        if (give <= 0) {
                            serverPlayer.sendMessage(Text.literal("You have none to give").formatted(Formatting.RED));
                        } else {
                            OrderFulfillment.Result result = OrderFulfillment.fulfill(parent.eco, serverPlayer, current.id, give);
                            switch (result.status()) {
                                case OK -> {
                                    String requesterName;
                                    ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(result.requester());
                                    if (requesterPlayer != null) {
                                        requesterName = IdentityCompat.of(requesterPlayer).name();
                                    } else {
                                        requesterName = parent.eco.getBestName(result.requester());
                                    }
                                    String extra = result.remaining() > 0 ? " (" + result.remaining() + " still wanted)" : "";
                                    serverPlayer.sendMessage(Text.literal("Fulfilled " + result.given() + "x " + result.item().getHoverName().getString() + " (" + requesterName + ") and earned " + EconomyCraft.formatMoney(result.payout()) + extra).formatted(Formatting.GREEN));
                                }
                                case REQUESTER_CANT_PAY -> serverPlayer.sendMessage(Text.literal("Requester can't pay").formatted(Formatting.RED));
                                case OWN_ORDER -> serverPlayer.sendMessage(Text.literal("You cannot fulfill your own request").formatted(Formatting.RED));
                                default -> serverPlayer.sendMessage(Text.literal("Request no longer available").formatted(Formatting.RED));
                            }
                        }
                    }

                    parent.updatePage();
                    player.closeHandledScreen();
                    OrdersUi.open(serverPlayer, parent.eco);
                    return;
                }

                if (slot == 6) {
                    player.closeHandledScreen();
                    OrdersUi.open((ServerPlayerEntity) player, parent.eco);
                    return;
                }
            }
            super.onSlotClick(slot, drag, type, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int idx) {
            return ItemStack.EMPTY;
        }
    }

    private static class RemoveMenu extends ScreenHandler {
        private final OrderRequest request;
        private final RequestMenu parent;
        private final SimpleInventory container = new SimpleInventory(9);

        RemoveMenu(int id, PlayerInventory inv, OrderRequest request, RequestMenu parent) {
            super(ScreenHandlerType.GENERIC_9X1, id);
            this.request = request;
            this.parent = parent;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Confirm").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));
            container.setStack(2, confirm);

            ItemStack item = request.item.copy();
            item.setCount(1);
            long tax = Math.round(request.price * EconomyConfig.get().taxRate);
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    createRewardLore(request.price, tax),
                    labeledValue("Amount", String.valueOf(request.amount), LABEL_PRIMARY_COLOR),
                    Text.literal("This will remove the request").styled(s -> s.withItalic(false).withColor(Formatting.RED))
            )));
            container.setStack(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel").styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.DARK_RED)));
            container.setStack(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return false;
                    }
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
            if (slot < 0 || slot >= this.slots.size()) {
                return;
            }
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

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int idx) {
            return ItemStack.EMPTY;
        }
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
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return idx < 45 && super.canTakeItems(player);
                    }
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
            int totalPages = (int) Math.ceil(items.size() / 45.0);
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
            ServerPlayerEntity viewer = getViewer();
            String name = viewer != null ? IdentityCompat.of(viewer).name() : eco.getBestName(owner);
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

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (slot < 0 || slot >= this.slots.size()) {
                return;
            }
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
                if (slot == navRowStart + 2 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 6 && (page + 1) * 45 < items.size()) {
                    page++;
                    updatePage();
                    return;
                }
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
