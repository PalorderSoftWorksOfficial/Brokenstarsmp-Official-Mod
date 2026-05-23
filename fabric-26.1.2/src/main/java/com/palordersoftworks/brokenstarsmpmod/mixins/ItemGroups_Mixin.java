package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.helpers.CreativeContext;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.item.ItemGroups.class)
public class ItemGroups_Mixin {
    @Inject(method = "updateEntries", at = @At("HEAD"))
    private static void start(ItemGroup.DisplayContext displayContext, CallbackInfo ci) {
        CreativeContext.IN_CREATIVE.set(true);
    }

    @Inject(method = "updateEntries", at = @At("RETURN"))
    private static void end(CallbackInfo ci) {
        CreativeContext.IN_CREATIVE.set(false);
    }
}
