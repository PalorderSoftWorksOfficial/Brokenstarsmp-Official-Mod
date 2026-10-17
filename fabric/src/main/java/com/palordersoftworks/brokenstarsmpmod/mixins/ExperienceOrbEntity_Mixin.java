package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbEntity_Mixin {

    @Redirect(
            method = "followNearbyPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"
            )
    )
    private Player brokenstarsmpmod$increaseRange(Level world, Entity entity, double distance) {
        double range = MechanicAccess.number("experience_orb_range", MechanicAccess.global(), ServerRules.EXPERIENCE_ORB_RANGE);
        return world.getNearestPlayer(entity, range);
    }
}