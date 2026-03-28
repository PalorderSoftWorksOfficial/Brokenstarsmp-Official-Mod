package com.reazip.economycraft.wand;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import net.minecraft.util.Formatting;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class SellWand {

    private SellWand() {}

    public static boolean isSellWand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GOLDEN_HOE)) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().getBoolean("sellWand").orElse(false);
    }

    public static int use(ServerPlayerEntity player, CommandSource source) {
        EconomyManager manager = EconomyCraft.getManager(player.getEntityWorld().getServer());
        PriceRegistry prices = manager.getPrices();

        long total = 0;
        int count = 0;

        var inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);

            if (stack.isEmpty()) continue;
            if (isSellWand(stack)) continue;

            long value = getStackValue(prices, stack);
            if (value <= 0) continue;

            total += value;
            count += stack.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }

        if (total <= 0) {
            player.sendMessage(Text.literal("No sellable items found.")
                    .formatted(Formatting.RED));
            return 0;
        }

        manager.addMoney(player.getUuid(), total);

        player.sendMessage(
                Text.literal("Sell Wand: Sold inventory for " +
                                EconomyCraft.formatMoney(total) + ".")
                        .formatted(Formatting.GOLD)
        );

        return count;
    }

    private static long getStackValue(PriceRegistry prices, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (prices.isSellBlockedByDamage(stack)) return 0;

        long container = getContainerValue(prices, stack);
        if (container > 0) return container * stack.getCount();

        Long unit = prices.getUnitSell(stack);
        if (unit == null) return 0;

        return unit * stack.getCount();
    }

    private static long getContainerValue(PriceRegistry prices, ItemStack stack) {
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        if (contents == null) return 0;

        long total = 0;

        for (ItemStack inner : contents.streamNonEmpty().toList()) {
            if (prices.isSellBlockedByDamage(inner)) continue;

            long innerContainer = getContainerValue(prices, inner);
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
}
