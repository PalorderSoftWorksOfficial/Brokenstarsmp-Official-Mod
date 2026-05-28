package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntity_AITickMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void limitAI(CallbackInfo ci) {
        if (ServerRules.MOB_AI_TICK_RANGE < 0) return;

        MobEntity self = (MobEntity)(Object)this;
        if (!(self.getEntityWorld() instanceof ServerWorld world)) return;

        PlayerEntity nearest = world.getClosestPlayer(self, ServerRules.MOB_AI_TICK_RANGE);
        if (nearest == null) {
            ci.cancel();
        }
    }
}