package com.palordersoftworks.brokenstarsmpmod.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Server shop UI title: "Server Shop" → "Shop".
 */
@Mixin(targets = "com.reazip.economycraft.shop.ServerShopUi", remap = false)
public abstract class ServerShopUi_Rename_Mixin {

    @ModifyConstant(method = "openRoot", constant = @Constant(stringValue = "Server Shop"))
    private static String brokenstarsmp$renameTitle(String original) {
        return "Shop";
    }
}
