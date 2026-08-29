package com.palordersoftworks.brokenstarsmpmod.listeners;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;

public class ItemMergeTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ServerRules.INSTANT_ITEM_MERGE) {
            return;
        }

        double radius = ServerRules.ITEM_MERGE_RADIUS;
        double radiusSq = radius * radius;

        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            Collection<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));

            for (Item item : items) {
                if (item.isDead()) continue;

                ItemStack stack = item.getItemStack();
                if (!stack.getType().isAir() && stack.getMaxStackSize() > 1) {
                    for (Item other : items) {
                        if (other == item || other.isDead()) continue;

                        ItemStack otherStack = other.getItemStack();
                        if (!otherStack.isSimilar(stack)) continue;

                        if (item.getLocation().distanceSquared(other.getLocation()) > radiusSq) continue;

                        int max = stack.getMaxStackSize();
                        int transfer = Math.min(otherStack.getAmount(), max - stack.getAmount());

                        if (transfer <= 0) continue;

                        stack.setAmount(stack.getAmount() + transfer);
                        item.setItemStack(stack);

                        otherStack.setAmount(otherStack.getAmount() - transfer);
                        if (otherStack.getAmount() <= 0) {
                            other.remove();
                        } else {
                            other.setItemStack(otherStack);
                        }
                    }
                }
            }
        }
    }
}
