package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.helpers.CreativeContext;
import com.palordersoftworks.brokenstarsmpmod.helpers.ShulkerStackHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FILLED_SHULKERS_STACK;

@Mixin(ItemStack.class)
public class ItemStack_stackableFilledShulkersMixin {

    @Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
    private static void stackableShulkerEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!FILLED_SHULKERS_STACK) return;
        if (CreativeContext.IN_CREATIVE.get()) return;

        if (!ShulkerStackHelper.isFilledShulker(a) || !ShulkerStackHelper.isFilledShulker(b)) return;

        ItemContainerContents containerA = a.get(DataComponents.CONTAINER);
        ItemContainerContents containerB = b.get(DataComponents.CONTAINER);
        if (containerA == containerB) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        if (containerA == null || containerB == null) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        cir.setReturnValue(containerA.equals(containerB));
        cir.cancel();
    }
}
