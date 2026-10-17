package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicAccess;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ItemEntity.class)
public abstract class ItemEntity_Mixin {

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 6000))
    private int brokenstarsmpmod$itemDespawnTicks(int original) {
        int rule = MechanicAccess.integer("item_despawn_time", MechanicAccess.global(), ServerRules.ITEM_DESPAWN_TICKS);
        return rule < 0 ? original : rule;
    }
}