package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobEntity_AITickMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void limitAI(CallbackInfo ci) {
        Mob self = (Mob)(Object)this;
        if (!(self.level() instanceof ServerLevel world)) return;
        int range = MechanicAccess.integer("mob_ai_tick_range", MechanicAccess.level(world), ServerRules.MOB_AI_TICK_RANGE);
        if (range < 0) return;

        Player nearest = world.getNearestPlayer(self, range);
        if (nearest == null) {
            ci.cancel();
        }
    }
}