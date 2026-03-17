package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.Rule;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FILLED_SHULKERS_STACK;

@Mixin(ItemStack.class)
public class ItemStack_stackableFilledShulkersMixin {

    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void stackFilledShulkers(CallbackInfoReturnable<Integer> cir) {
        if (!FILLED_SHULKERS_STACK) return;

        ItemStack stack = (ItemStack) (Object) this;

        if (stack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null && container.stream().findAny().isPresent()) {
                cir.setReturnValue(64);
            }
        }
    }

    @Inject(method = "setCount", at = @At("HEAD"), cancellable = true)
    private void capShulkerStackSize(int count, CallbackInfo ci) {
        if (!FILLED_SHULKERS_STACK) return;

        ItemStack stack = (ItemStack) (Object) this;

        if (stack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof ShulkerBoxBlock) {

            if (count > 64) {
                try {
                    java.lang.reflect.Field countField = ItemStack.class.getDeclaredField("count");
                    countField.setAccessible(true);
                    countField.setInt(stack, 64);
                    ci.cancel();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Inject(method = "areItemsAndComponentsEqual", at = @At("HEAD"), cancellable = true)
    private static void stackableShulkerEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!FILLED_SHULKERS_STACK) return;

        if (!(a.getItem() instanceof BlockItem blockA) || !(b.getItem() instanceof BlockItem blockB)) return;
        if (!(blockA.getBlock() instanceof ShulkerBoxBlock) || !(blockB.getBlock() instanceof ShulkerBoxBlock)) return;

        ContainerComponent containerA = a.get(DataComponentTypes.CONTAINER);
        ContainerComponent containerB = b.get(DataComponentTypes.CONTAINER);

        if (containerA == null || containerB == null) return;
        Optional<?> anyA = containerA.stream().findAny();
        Optional<?> anyB = containerB.stream().findAny();
        if (anyA.isEmpty() || anyB.isEmpty()) return;

        if (anyA.equals(anyB)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}