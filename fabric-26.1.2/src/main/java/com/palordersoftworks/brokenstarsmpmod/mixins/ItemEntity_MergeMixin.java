package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemEntity.class)
public abstract class ItemEntity_MergeMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void mergeNearby(CallbackInfo ci) {
        if (!ServerRules.INSTANT_ITEM_MERGE) return;

        ItemEntity self = (ItemEntity)(Object)this;
        World world = self.getEntityWorld();

        if (world.isClient()) return;

        double radius = ServerRules.ITEM_MERGE_RADIUS;

        List<ItemEntity> items = world.getEntitiesByClass(
                ItemEntity.class,
                new Box(self.getBlockPos()).expand(radius),
                e -> e != self
        );

        for (ItemEntity other : items) {
            if (other.isRemoved()) continue;

            ItemStack a = self.getStack();
            ItemStack b = other.getStack();

            if (!ItemStack.areItemsAndComponentsEqual(a, b)) continue;

            if (!a.isStackable()) continue;

            int max = a.getMaxCount();
            int transfer = Math.min(b.getCount(), max - a.getCount());

            if (transfer <= 0) continue;

            a.increment(transfer);
            b.decrement(transfer);

            if (b.isEmpty()) {
                other.discard();
            }
        }
    }
}