package com.palordersoftworks.brokenstarsmpmod.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Hub button labels: Server Shop → Shop, Player Shop → Auction House.
 */
@Mixin(targets = "com.reazip.economycraft.HubUi$HubMenu", remap = false)
public abstract class HubUi_ShopRename_Mixin {

    @ModifyConstant(method = "render", constant = @Constant(stringValue = "Server Shop"))
    private String brokenstarsmp$renameServerShop(String original) {
        return "Shop";
    }

    @ModifyConstant(method = "render", constant = @Constant(stringValue = "Player Shop"))
    private String brokenstarsmp$renamePlayerShop(String original) {
        return "Auction House";
    }
}
