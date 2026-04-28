package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntity_LifetimeMixin {

    private int ageCustom = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void limitLifetime(CallbackInfo ci) {
        if (ServerRules.PROJECTILE_LIFETIME < 0) return;

        ProjectileEntity self = (ProjectileEntity)(Object)this;
        if (self.getEntityWorld().isClient()) return;

        ageCustom++;
        if (ageCustom > ServerRules.PROJECTILE_LIFETIME) {
            self.discard();
        }
    }
}