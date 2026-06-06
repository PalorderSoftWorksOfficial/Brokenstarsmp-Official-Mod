package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.commands.PermissionUtil;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntity_Mixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void removeFakePlayerInvulnerability(ServerWorld world, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class<?> fakeClass = Class.forName("carpet.patches.EntityPlayerMPFake");
            if (fakeClass.isInstance(this)) {
                cir.setReturnValue(false);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$smartTotemImmortality(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
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

    private static boolean holdsTotem(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }
}
