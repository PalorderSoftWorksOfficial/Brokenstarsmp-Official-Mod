package com.palordersoftworks.economycraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.palordersoftworks.economycraft.PriceRegistry.ResolvedPrice;
import com.palordersoftworks.economycraft.util.ChatCompat;
import com.palordersoftworks.economycraft.util.IdentifierCompat;
import net.minecraft.util.Formatting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SellCommand {
    private static final Map<UUID, PendingSale> PENDING = new HashMap<>();
    private static final long CONFIRM_EXPIRY_MS = 20_000L;

    private SellCommand() {}

    public static LiteralArgumentBuilder<ServerCommandSource> register() {
        return literal("sell")
                .then(literal("all")
                        .then(literal("inventory").executes(SellCommand::sellInventory))
                        .then(literal("shards").then(literal("inventory").executes(SellCommand::sellInventoryShards)))
                        .executes(SellCommand::previewSellAll)
                        .then(literal("confirm").executes(SellCommand::confirmSellAll)))
                .then(literal("shards")
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> sellMainHandShards(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
                        .executes(ctx -> sellMainHandShards(ctx, -1)))
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> sellMainHand(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
                .executes(ctx -> sellMainHand(ctx, -1));
    }

    private static int sellMainHand(CommandContext<ServerCommandSource> ctx, int amount) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            source.sendError(Text.literal("You are not holding any item.").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        if (prices.isSellBlockedByDamage(hand)) {
            source.sendError(Text.literal("Damaged items cannot be sold.").formatted(Formatting.RED));
            return 0;
        }

        int available = hand.getCount();
        int toSell = amount < 0 ? available : amount;
        if (toSell < 1 || toSell > available) {
            source.sendError(Text.literal("Invalid amount.").formatted(Formatting.RED));
            return 0;
        }

        long containerValue = getContainerSellValue(prices, hand);
        Long unitSell = prices.getUnitSell(hand);
        Long total;

        if (containerValue > 0) {
            total = safeMultiply(containerValue, toSell);
        } else {
            if (unitSell == null) {
                source.sendError(Text.literal("This item cannot be sold.").formatted(Formatting.RED));
                return 0;
            }
            total = safeMultiply(unitSell, toSell);
        }

        if (total == null) {
            source.sendError(Text.literal("Sale amount is too large.").formatted(Formatting.RED));
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && !manager.tryRecordDailySell(player.getUuid(), total)) {
            return handleDailyLimitFailure(manager, player, source);
        }

        hand.decrement(toSell);
        if (hand.isEmpty()) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }

        manager.addMoney(player.getUuid(), total);
        player.sendMessage(Text.literal("Sold for " + EconomyCraft.formatMoney(total) + ".").formatted(Formatting.GREEN));
        return toSell;
    }

    private static int previewSellAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            source.sendError(Text.literal("You are not holding any item.").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        if (prices.isSellBlockedByDamage(hand)) {
            source.sendError(Text.literal("Damaged items cannot be sold.").formatted(Formatting.RED));
            return 0;
        }

        ResolvedPrice resolved = prices.resolve(hand);
        Long unitSell = prices.getUnitSell(hand);
        long containerValue = getContainerSellValue(prices, hand);

        IdentifierCompat.Id key = resolveSellKey(hand, resolved);
        if (key == null || (resolved == null && unitSell == null && containerValue <= 0)) {
            source.sendError(Text.literal("This item cannot be sold.").formatted(Formatting.RED));
            return 0;
        }

        int totalCount = countMatchingSellable(player, prices, key);
        if (totalCount <= 0) {
            source.sendError(Text.literal("This item cannot be sold.").formatted(Formatting.RED));
            return 0;
        }

        long total = 0;
        var inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (isMatchingSellable(prices, stack, key)) {
                total += getStackTotalValue(prices, stack);
            }
        }

        ItemStack offhand = player.getOffHandStack();
        if (isMatchingSellable(prices, offhand, key)) {
            total += getStackTotalValue(prices, offhand);
        }

        IdentifierCompat.Id heldItemId = IdentifierCompat.wrap(net.minecraft.registry.Registries.ITEM.getId(hand.getItem()));
        PENDING.put(player.getUuid(), new PendingSale(key, totalCount, total,
                System.currentTimeMillis() + CONFIRM_EXPIRY_MS, heldItemId));

        MutableText base = Text.literal("Sell all for " + EconomyCraft.formatMoney(total) + ". ")
                .formatted(Formatting.YELLOW);

        ClickEvent ev = ChatCompat.runCommandEvent("/sell all confirm");
        if (ev != null) {
            player.sendMessage(base.append(Text.literal("[CONFIRM]")
                    .styled(s -> s.withUnderline(true).withColor(Formatting.GREEN).withClickEvent(ev))));
        } else {
            player.sendMessage(base);
            ChatCompat.sendRunCommandTellraw(player, "", "[CONFIRM]", "/sell all confirm");
        }

        return totalCount;
    }

    private static int sellMainHandShards(CommandContext<ServerCommandSource> ctx, int amount) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;
        if (!EconomyConfig.get().shardsEnabled) {
            source.sendError(Text.literal("Shards are disabled.").formatted(Formatting.RED));
            return 0;
        }

        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            source.sendError(Text.literal("You are not holding any item.").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();
        if (prices.isSellBlockedByDamage(hand)) {
            source.sendError(Text.literal("Damaged items cannot be sold.").formatted(Formatting.RED));
            return 0;
        }

        int available = hand.getCount();
        int toSell = amount < 0 ? available : amount;
        if (toSell < 1 || toSell > available) {
            source.sendError(Text.literal("Invalid amount.").formatted(Formatting.RED));
            return 0;
        }

        Long unitSell = prices.getUnitSellShards(hand);
        if (unitSell == null) {
            source.sendError(Text.literal("This item cannot be sold for shards.").formatted(Formatting.RED));
            return 0;
        }
        Long total = safeMultiply(unitSell, toSell);
        if (total == null) {
            source.sendError(Text.literal("Sale amount is too large.").formatted(Formatting.RED));
            return 0;
        }

        hand.decrement(toSell);
        if (hand.isEmpty()) player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        manager.addShards(player.getUuid(), total);
        player.sendMessage(Text.literal("Sold for " + EconomyCraft.formatShards(total) + ".").formatted(Formatting.LIGHT_PURPLE));
        return toSell;
    }

    private static int confirmSellAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        PendingSale pending = PENDING.get(player.getUuid());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            source.sendError(Text.literal("No pending sale. Run /sell all again.").formatted(Formatting.RED));
            PENDING.remove(player.getUuid());
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        if (EconomyConfig.get().dailySellLimit > 0 && !manager.tryRecordDailySell(player.getUuid(), pending.total())) {
            return handleDailyLimitFailure(manager, player, source);
        }

        removeMatching(player, manager.getPrices(), pending.key(), pending.count());
        manager.addMoney(player.getUuid(), pending.total());

        player.sendMessage(Text.literal("Sold for " + EconomyCraft.formatMoney(pending.total()) + ".").formatted(Formatting.GREEN));
        PENDING.remove(player.getUuid());
        return pending.count();
    }

    private static int sellInventory(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        long total = 0;
        int count = 0;

        var inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            long value = getStackTotalValue(prices, stack);
            if (value <= 0) continue;

            total += value;
            count += stack.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }

        if (total <= 0) {
            source.sendError(Text.literal("No sellable items found.").formatted(Formatting.RED));
            return 0;
        }

        manager.addMoney(player.getUuid(), total);
        player.sendMessage(Text.literal("Sold inventory for " + EconomyCraft.formatMoney(total) + ".").formatted(Formatting.GREEN));
        return count;
    }

    private static int sellInventoryShards(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;
        if (!EconomyConfig.get().shardsEnabled) {
            source.sendError(Text.literal("Shards are disabled.").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        PriceRegistry prices = manager.getPrices();

        long total = 0;
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || prices.isSellBlockedByDamage(stack)) continue;
            Long unit = prices.getUnitSellShards(stack);
            if (unit == null) continue;
            total += unit * stack.getCount();
            count += stack.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (total <= 0) {
            source.sendError(Text.literal("No shard-sellable items found.").formatted(Formatting.RED));
            return 0;
        }
        manager.addShards(player.getUuid(), total);
        player.sendMessage(Text.literal("Sold inventory for " + EconomyCraft.formatShards(total) + ".").formatted(Formatting.LIGHT_PURPLE));
        return count;
    }

    private static long getStackTotalValue(PriceRegistry prices, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (prices.isSellBlockedByDamage(stack)) return 0;

        long container = getContainerSellValue(prices, stack);
        if (container > 0) return container * stack.getCount();

        Long price = prices.getUnitSell(stack);
        if (price == null) return 0;

        return price * stack.getCount();
    }

    private static long getContainerSellValue(PriceRegistry prices, ItemStack stack) {
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        if (contents == null) return 0;

        long total = 0;
        for (ItemStack inner : contents.streamNonEmpty().toList()) {
            if (prices.isSellBlockedByDamage(inner)) continue;

            long innerContainer = getContainerSellValue(prices, inner);
            if (innerContainer > 0) {
                total += innerContainer;
                continue;
            }

            Long price = prices.getUnitSell(inner);
            if (price != null) {
                total += price * inner.getCount();
            }
        }
        return total;
    }

    private static int countMatchingSellable(ServerPlayerEntity player, PriceRegistry prices, IdentifierCompat.Id key) {
        var inv = player.getInventory();
        int total = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (isMatchingSellable(prices, stack, key)) {
                total += stack.getCount();
            }
        }

        ItemStack offhand = player.getOffHandStack();
        if (isMatchingSellable(prices, offhand, key)) {
            total += offhand.getCount();
        }
        return total;
    }

    private static void removeMatching(ServerPlayerEntity player, PriceRegistry prices, IdentifierCompat.Id key, int toRemove) {
        var inv = player.getInventory();
        int remaining = toRemove;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            remaining = drainStack(prices, stack, key, remaining);
            if (stack.isEmpty()) {
                inv.setStack(i, ItemStack.EMPTY);
            }
            if (remaining <= 0) return;
        }

        ItemStack offhand = player.getOffHandStack();
        remaining = drainStack(prices, offhand, key, remaining);
        if (offhand.isEmpty()) {
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static int drainStack(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key, int remaining) {
        if (remaining <= 0) return 0;
        if (!isMatchingSellable(prices, stack, key)) return remaining;

        int remove = Math.min(remaining, stack.getCount());
        stack.decrement(remove);
        return remaining - remove;
    }

    private static boolean isMatchingSellable(PriceRegistry prices, ItemStack stack, IdentifierCompat.Id key) {
        if (stack == null || stack.isEmpty()) return false;
        if (prices.isSellBlockedByDamage(stack)) return false;

        ResolvedPrice rp = prices.resolve(stack);
        if (rp != null) {
            return key.equals(rp.key());
        }

        IdentifierCompat.Id itemId = IdentifierCompat.wrap(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()));
        return key.equals(itemId) && prices.getUnitSell(stack) != null;
    }

    private static IdentifierCompat.Id resolveSellKey(ItemStack stack, ResolvedPrice resolved) {
        if (resolved != null) return resolved.key();
        if (stack == null || stack.isEmpty()) return null;
        return IdentifierCompat.wrap(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()));
    }

    private static Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static ServerPlayerEntity getPlayer(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command.").formatted(Formatting.RED));
            return null;
        }
    }

    private record PendingSale(IdentifierCompat.Id key, int count, long total, long expiresAt,
                               IdentifierCompat.Id heldItemId) {}

    private static int handleDailyLimitFailure(EconomyManager manager, ServerPlayerEntity player, ServerCommandSource source) {
        long remaining = manager.getDailySellRemaining(player.getUuid());
        long limit = EconomyConfig.get().dailySellLimit;

        if (remaining <= 0) {
            source.sendError(Text.literal("Daily sell limit of " + EconomyCraft.formatMoney(limit) + " reached. Try again tomorrow.")
                    .formatted(Formatting.RED));
        } else {
            source.sendError(Text.literal("This sale exceeds the daily sell limit of " +
                            EconomyCraft.formatMoney(limit) + ". You can sell items worth " +
                            EconomyCraft.formatMoney(remaining) + " more today.")
                    .formatted(Formatting.RED));
        }
        return 0;
    }
}