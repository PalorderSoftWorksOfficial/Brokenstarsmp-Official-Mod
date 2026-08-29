package com.palordersoftworks.brokenstarsmpmod.economy.wand;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SellWand {
    private SellWand() {}

    public static ItemStack createSellWandItem() {
        ItemStack stack = new ItemStack(Items.GOLDEN_HOE);
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("sellWand", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Sell ")
                        .withStyle(s -> s.withItalic(true).withBold(true).withColor(ChatFormatting.GOLD))
                        .append(Component.literal("Wand")
                                .withStyle(s -> s.withItalic(true).withBold(true).withColor(ChatFormatting.GOLD)))
        );
        return stack;
    }

    public static boolean isSellWand(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.GOLDEN_HOE)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr("sellWand", false);
    }

    public static int useOnTargetContainer(ServerPlayer player) {
        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            player.sendSystemMessage(Component.literal("Look at a container to use the Sell Wand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Container inv = resolveTargetInventory(player, bhr.getBlockPos());
        if (inv == null) {
            player.sendSystemMessage(Component.literal("That block is not a sellable container.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(player.level().getServer());
        PriceRegistry prices = manager.getPrices();
        long total = 0;
        int sold = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            long value = getStackValue(prices, stack);
            if (value <= 0) continue;
            total += value;
            sold += stack.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();

        if (total <= 0) {
            player.sendSystemMessage(Component.literal("No sellable items found in that container.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        manager.addMoney(player.getUUID(), total);
        player.sendSystemMessage(Component.literal(
                "Sell Wand: Sold container for " + EconomyCraft.formatMoney(total) + ".")
                .withStyle(ChatFormatting.GOLD));
        return sold;
    }

    public static int usePlayerInventory(ServerPlayer player) {
        EconomyManager manager = EconomyCraft.getManager(player.level().getServer());
        PriceRegistry prices = manager.getPrices();
        Inventory inv = player.getInventory();
        long total = 0;
        int sold = 0;

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || isSellWand(stack)) continue;
            long value = getStackValue(prices, stack);
            if (value <= 0) continue;
            total += value;
            sold += stack.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }

        if (total <= 0) {
            player.sendSystemMessage(Component.literal("No sellable items found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        manager.addMoney(player.getUUID(), total);
        player.sendSystemMessage(Component.literal(
                "Sell Wand: Sold inventory for " + EconomyCraft.formatMoney(total) + ".")
                .withStyle(ChatFormatting.GOLD));
        return sold;
    }

    private static Container resolveTargetInventory(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof ChestBlock chestBlock) {
            Container chestInv = ChestBlock.getContainer(chestBlock, state, player.level(), pos, true);
            if (chestInv != null) return chestInv;
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof Container container) return container;
        return null;
    }

    private static long getStackValue(PriceRegistry prices, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (prices.isSellBlockedByDamage(stack) || prices.isSellBlockedByContents(stack)) return 0;

        long container = getContainerValue(prices, stack);
        if (container > 0) return container * stack.getCount();

        Long unit = prices.getUnitSell(stack);
        return unit == null ? 0 : unit * stack.getCount();
    }

    private static long getContainerValue(PriceRegistry prices, ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return 0;

        long total = 0;
        for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
            if (prices.isSellBlockedByDamage(inner)) continue;
            long nested = getContainerValue(prices, inner);
            if (nested > 0) {
                total += nested;
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
