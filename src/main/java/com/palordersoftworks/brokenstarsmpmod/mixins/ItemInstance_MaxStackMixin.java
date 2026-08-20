package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.helpers.CreativeContext;
import com.palordersoftworks.brokenstarsmpmod.helpers.ShulkerStackHelper;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FILLED_SHULKERS_STACK;

@Mixin(ItemInstance.class)
public interface ItemInstance_MaxStackMixin {

    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    default void stackFilledShulkers(CallbackInfoReturnable<Integer> cir) {
        if (!FILLED_SHULKERS_STACK) return;
        if (CreativeContext.IN_CREATIVE.get()) return;
        if (!((Object) this instanceof ItemStack stack)) return;

        if (ShulkerStackHelper.isFilledShulker(stack)) {
            cir.setReturnValue(64);
        }
    }
}
