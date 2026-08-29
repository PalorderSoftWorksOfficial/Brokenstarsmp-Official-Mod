package com.palordersoftworks.brokenstarsmpmod.helpers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ShulkerStackHelper {
    private ShulkerStackHelper() {}

    public static boolean isFilledShulker(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return false;
        return container.nonEmptyItems().iterator().hasNext();
    }
}
