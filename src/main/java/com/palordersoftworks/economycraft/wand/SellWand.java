package com.palordersoftworks.economycraft.wand;

import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.PriceRegistry;
import com.palordersoftworks.economycraft.EconomyCraft;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;

public final class SellWand {

    private SellWand() {}

    public static ItemStack createSellWandItem() {
        ItemStack stack = new ItemStack(Items.GOLDEN_HOE);
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putBoolean("sellWand", true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    public static boolean isSellWand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GOLDEN_HOE)) return false;

        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().getBoolean("sellWand", false);
    }

    public static int use(ServerPlayerEntity player) {
        return usePlayerInventory(player);
    }

    public static int useOnTargetContainer(ServerPlayerEntity player) {
        HitResult hit = player.raycast(5.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Look at a container to use the Sell Wand.").formatted(Formatting.RED));
            return 0;
        }

        BlockPos pos = bhr.getBlockPos();
        Inventory inv = resolveTargetInventory(player, pos);
        if (inv == null) {
            player.sendMessage(Text.literal("That block is not a sellable container.").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(player.getEntityWorld().getServer());
        PriceRegistry prices = manager.getPrices();
        long total = 0;
        int soldStacks = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            long value = getStackValue(prices, stack);
            if (value <= 0) continue;
            total += value;
            soldStacks += stack.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }
        inv.markDirty();

        if (total <= 0) {
            player.sendMessage(Text.literal("No sellable items found in that container.").formatted(Formatting.RED));
            return 0;
        }

        manager.addMoney(player.getUuid(), total);
        player.sendMessage(
                Text.literal("Sell Wand: Sold container for " + EconomyCraft.formatMoney(total) + ".")
                        .formatted(Formatting.GOLD)
        );
        return soldStacks;
    }

    private static Inventory resolveTargetInventory(ServerPlayerEntity player, BlockPos pos) {
        var world = player.getEntityWorld();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof ChestBlock chestBlock) {
            // For double chests this returns a combined inventory.
            Object chestInv = ChestBlock.getInventory(chestBlock, state, world, pos, true);
            if (chestInv instanceof Inventory inventory) {
                return inventory;
            }
        }
        var be = world.getBlockEntity(pos);
        if (be instanceof Inventory inventory) {
            return inventory;
        }
        return null;
    }

    private static int usePlayerInventory(ServerPlayerEntity player) {
        EconomyManager manager = EconomyCraft.getManager(player.getEntityWorld().getServer());
        PriceRegistry prices = manager.getPrices();

        long total = 0;
        int soldStacks = 0;

        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (isSellWand(stack)) continue;

            long value = getStackValue(prices, stack);
            if (value <= 0) continue;

            total += value;
            soldStacks += stack.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }

        if (total <= 0) {
            player.sendMessage(Text.literal("No sellable items found.").formatted(Formatting.RED));
            return 0;
        }

        manager.addMoney(player.getUuid(), total);

        player.sendMessage(
                Text.literal("Sell Wand: Sold inventory for " + EconomyCraft.formatMoney(total) + ".")
                        .formatted(Formatting.GOLD)
        );

        return soldStacks;
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