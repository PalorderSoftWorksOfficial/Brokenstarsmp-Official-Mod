package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.TeamRules;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayer_TeamFireMixin {
    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$noTeamFriendlyFire(ServerLevel level, DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(damageSource.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        ServerPlayer victim = (ServerPlayer) (Object) this;
        if (attacker == victim) {
            return;
        }
        if (TeamManager.isSameTeam(attacker.getUUID(), victim.getUUID())
                && (TeamRules.TEAM_NO_PVP || TeamRules.TEAM_FRIENDLY_FIRE)) {
            cir.setReturnValue(false);
        }
    }
}
