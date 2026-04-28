package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntity_NoSplitMixin {

    @Inject(
            method = "spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void noSplit(ServerWorld world, Vec3d pos, int amount, CallbackInfo ci) {
        if (!ServerRules.NO_XP_SPLIT) return;
        world.spawnEntity(new ExperienceOrbEntity(world, pos, Vec3d.ZERO, amount));

        ci.cancel();
    }
}