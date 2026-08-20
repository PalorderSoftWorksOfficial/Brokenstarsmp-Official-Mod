package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ItemEntity.class)
public abstract class ItemEntity_Mixin {

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 6000))
    private int brokenstarsmpmod$itemDespawnTicks(int original) {
        int rule = ServerRules.ITEM_DESPAWN_TICKS;
        return rule < 0 ? original : rule;
    }
}