package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.FILLED_SHULKERS_STACK;

@Mixin(ItemStack.class)
public class ItemStack_stackableFilledShulkersMixin {

    private static boolean isCreativeTabPopulation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stackTrace) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();

            if ("net.minecraft.item.ItemGroup".equals(className) && "updateEntries".equals(methodName)) {
                return true;
            }

            if ("net.minecraft.item.ItemGroups".equals(className) && "updateEntries".equals(methodName)) {
                return true;
            }

            if (className.contains("class_1761") &&
                    ("method_47306".equals(methodName) ||
                            "method_47330".equals(methodName) ||
                            "method_47337".equals(methodName) ||
                            "method_51336".equals(methodName))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFilledShulker(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        return container != null && container.stream().findAny().isPresent();
    }

    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void stackFilledShulkers(CallbackInfoReturnable<Integer> cir) {
        if (!FILLED_SHULKERS_STACK) return;
        if (isCreativeTabPopulation()) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (isFilledShulker(stack)) {
            cir.setReturnValue(64);
            cir.cancel();
        }
    }

    @Inject(method = "areItemsAndComponentsEqual", at = @At("HEAD"), cancellable = true)
    private static void stackableShulkerEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!FILLED_SHULKERS_STACK) return;
        if (isCreativeTabPopulation()) return;

        if (!isFilledShulker(a) || !isFilledShulker(b)) return;

        ContainerComponent containerA = a.get(DataComponentTypes.CONTAINER);
        ContainerComponent containerB = b.get(DataComponentTypes.CONTAINER);

        if (containerA == null || containerB == null) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        cir.setReturnValue(containerA.equals(containerB));
        cir.cancel();
    }
}