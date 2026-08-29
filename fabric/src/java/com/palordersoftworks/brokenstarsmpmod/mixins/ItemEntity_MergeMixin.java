package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

@Mixin(ItemEntity.class)
public abstract class ItemEntity_MergeMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void mergeNearby(CallbackInfo ci) {
        if (!ServerRules.INSTANT_ITEM_MERGE) return;

        ItemEntity self = (ItemEntity)(Object)this;
        Level world = self.level();

        if (world.isClientSide()) return;

        double radius = ServerRules.ITEM_MERGE_RADIUS;

        List<ItemEntity> items = world.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(self.blockPosition()).inflate(radius),
                e -> e != self
        );

        for (ItemEntity other : items) {
            if (other.isRemoved()) continue;

            ItemStack a = self.getItem();
            ItemStack b = other.getItem();

            if (!ItemStack.isSameItemSameComponents(a, b)) continue;

            if (!a.isStackable()) continue;

            int max = a.getMaxStackSize();
            int transfer = Math.min(b.getCount(), max - a.getCount());

            if (transfer <= 0) continue;

            a.grow(transfer);
            b.shrink(transfer);

            if (b.isEmpty()) {
                other.discard();
            }
        }
    }
}