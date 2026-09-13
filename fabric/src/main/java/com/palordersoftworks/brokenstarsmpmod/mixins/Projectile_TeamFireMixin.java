package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.TeamRules;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class Projectile_TeamFireMixin {
    @Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$noTeamProjectileFire(EntityHitResult hitResult, CallbackInfo ci) {
        if (!TeamRules.TEAM_PROJECTILE_FRIENDLY_FIRE && !TeamRules.TEAM_NO_PVP) {
            return;
        }
        Projectile projectile = (Projectile) (Object) this;
        if (!(projectile.getOwner() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!(hitResult.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (attacker == victim) {
            return;
        }
        if (TeamManager.isSameTeam(attacker.getUUID(), victim.getUUID())) {
            ci.cancel();
        }
    }
}
