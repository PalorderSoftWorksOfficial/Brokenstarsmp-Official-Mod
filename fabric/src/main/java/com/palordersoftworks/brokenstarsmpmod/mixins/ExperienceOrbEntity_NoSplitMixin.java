package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbEntity_NoSplitMixin {

    @Inject(
            method = "award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void noSplit(ServerLevel world, Vec3 pos, int amount, CallbackInfo ci) {
        if (MechanicAccess.bool("xp_splitting_enabled", MechanicAccess.global(), !ServerRules.NO_XP_SPLIT)) return;
        world.addFreshEntity(new ExperienceOrb(world, pos, Vec3.ZERO, amount));

        ci.cancel();
    }
}