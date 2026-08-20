package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.commands.PermissionUtil;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntity_Mixin {
    @Inject(method = "isInvulnerableTo(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
    private void removeFakePlayerInvulnerability(ServerLevel world, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class<?> fakeClass = Class.forName("carpet.patches.EntityPlayerMPFake");
            if (fakeClass.isInstance(this)) {
                cir.setReturnValue(false);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$smartTotemImmortality(ServerLevel serverWorld, DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!UnstableSMPRules.IMMORTAL_SYSTEM_ENABLED) {
            return;
        }
        if (!PermissionUtil.hasImmortalPermission(player)) {
            return;
        }
        if (!UnstableSMPFeatures.isImmortal(player)) {
            return;
        }
        if (UnstableSMPRules.SMART_TOTEM_DETECTION && holdsTotem(player)) {
            return;
        }
        cir.setReturnValue(false);
    }

    private static boolean holdsTotem(ServerPlayer player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }
}
