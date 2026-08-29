package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.helpers.CreativeContext;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.item.CreativeModeTabs.class)
public class ItemGroups_Mixin {
    @Inject(method = "buildAllTabContents", at = @At("HEAD"))
    private static void start(CreativeModeTab.ItemDisplayParameters displayContext, CallbackInfo ci) {
        CreativeContext.IN_CREATIVE.set(true);
    }

    @Inject(method = "buildAllTabContents", at = @At("RETURN"))
    private static void end(CreativeModeTab.ItemDisplayParameters displayContext, CallbackInfo ci) {
        CreativeContext.IN_CREATIVE.set(false);
    }
}
