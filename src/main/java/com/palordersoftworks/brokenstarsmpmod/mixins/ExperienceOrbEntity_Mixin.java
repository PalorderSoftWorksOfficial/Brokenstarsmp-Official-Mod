package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExperienceOrbEntity.class)
public class ExperienceOrbEntity_Mixin {

    @Redirect(
            method = "moveTowardsPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/PlayerEntity;"
            )
    )
    private PlayerEntity brokenstarsmpmod$increaseRange(World world, Entity entity, double distance) {
        return world.getClosestPlayer(entity, 64.0);
    }
}