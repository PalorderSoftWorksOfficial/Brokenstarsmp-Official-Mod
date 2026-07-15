package com.palordersoftworks.economycraft.orders;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.ChatCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;

import java.util.UUID;

public final class OrderFulfillment {
    private OrderFulfillment() {}

    public enum Status {
        OK,
        ORDER_GONE,
        OWN_ORDER,
        INVALID_AMOUNT,
        NOT_ENOUGH_ITEMS,
        REQUESTER_CANT_PAY
    }

    public record Result(Status status, int given, long payout, int remaining, ItemStack item, UUID requester) {
        public boolean success() {
            return status == Status.OK;
        }
    }

    public static Result fulfill(EconomyManager eco, ServerPlayerEntity fulfiller, int orderId, int requestedAmount) {
        OrderManager orders = eco.getOrders();
        OrderRequest order = orders.getRequest(orderId);
        if (order == null || order.item == null || order.item.isEmpty()) {
            return new Result(Status.ORDER_GONE, 0, 0L, 0, ItemStack.EMPTY, null);
        }
        if (fulfiller.getUuid().equals(order.requester)) {
            return new Result(Status.OWN_ORDER, 0, 0L, order.amount, order.item.copy(), order.requester);
        }

        int give = requestedAmount <= 0 ? order.amount : Math.min(requestedAmount, order.amount);
        if (give <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0, 0L, order.amount, order.item.copy(), order.requester);
        }

        if (countHeld(fulfiller, order.item) < give) {
            return new Result(Status.NOT_ENOUGH_ITEMS, 0, 0L, order.amount, order.item.copy(), order.requester);
        }

        long payment = order.amount <= 0 ? 0L : Math.round((double) order.price * give / order.amount);
        payment = Math.min(payment, order.price);

        long balance = eco.getBalance(order.requester, true);
        if (balance < payment) {
            return new Result(Status.REQUESTER_CANT_PAY, 0, 0L, order.amount, order.item.copy(), order.requester);
        }

        ItemStack itemProto = order.item.copy();
        UUID requester = order.requester;

        removeItems(fulfiller, itemProto, give);

        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        long payout = payment - tax;

        eco.removeMoney(requester, payment);
        eco.addMoney(fulfiller.getUuid(), payout);

        deliver(orders, requester, itemProto, give);

        order.amount -= give;
        order.price -= payment;

        int remaining = order.amount;
        if (remaining <= 0) {
            orders.removeRequest(order.id);
            remaining = 0;
        } else {
            orders.markChanged();
        }

        notifyRequester(eco.getServer(), requester, give, itemProto);
        return new Result(Status.OK, give, payout, remaining, itemProto, requester);
    }

    public static int countHeld(ServerPlayerEntity player, ItemStack proto) {
        if (proto == null || proto.isEmpty()) return 0;
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(proto.getItem())) total += stack.getCount();
        }
        return total;
    }

    public static long payoutFor(OrderRequest order, int give) {
        if (order == null || order.amount <= 0 || give <= 0) return 0L;
        long payment = Math.min(Math.round((double) order.price * give / order.amount), order.price);
        long tax = Math.round(payment * EconomyConfig.get().taxRate);
        return payment - tax;
    }

    private static void removeItems(ServerPlayerEntity player, ItemStack proto, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(proto.getItem())) {
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
                if (remaining <= 0) return;
            }
        }
    }

    private static void deliver(OrderManager orders, UUID requester, ItemStack proto, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(proto.getMaxCount(), remaining);
            orders.addDelivery(requester, new ItemStack(proto.getItem(), count));
            remaining -= count;
        }
    }

    private static void notifyRequester(MinecraftServer server, UUID requester, int amount, ItemStack item) {
        ServerPlayerEntity requesterPlayer = server.getPlayerManager().getPlayer(requester);
        if (requesterPlayer == null) return;

        String itemName = item.getHoverName().getString();
        String prefix = amount + "x " + itemName + " of your request has been fulfilled: ";

        ClickEvent event = ChatCompat.runCommandEvent("/eco orders claim");
        if (event != null) {
            requesterPlayer.sendMessage(Text.literal(prefix)
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal("[Claim]")
                            .styled(s -> s.withUnderline(true).withColor(Formatting.GREEN).withClickEvent(event))));
        } else {
            ChatCompat.sendRunCommandTellraw(requesterPlayer, prefix, "[Claim]", "/eco orders claim");
        }
    }
}
