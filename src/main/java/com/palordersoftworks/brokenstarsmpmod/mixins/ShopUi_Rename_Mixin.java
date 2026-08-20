package com.palordersoftworks.brokenstarsmpmod.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Player shop UI title: "Shop" → "Auction House".
 */
@Mixin(targets = "com.reazip.economycraft.shop.ShopUi", remap = false)
public abstract class ShopUi_Rename_Mixin {

    @ModifyConstant(
            method = "open(Lnet/minecraft/server/level/ServerPlayer;Lcom/reazip/economycraft/shop/ShopManager;ILjava/lang/String;Lcom/reazip/economycraft/util/SortMode;Z)V",
            constant = @Constant(stringValue = "Shop")
    )
    private static String brokenstarsmp$renameTitle(String original) {
        return "Auction House";
    }
}
