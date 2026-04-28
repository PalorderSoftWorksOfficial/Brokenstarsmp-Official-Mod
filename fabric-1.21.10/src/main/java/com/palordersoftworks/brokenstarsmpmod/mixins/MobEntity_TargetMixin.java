package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntity_TargetMixin {

    @Shadow
    private @Nullable LivingEntity target;

    @Inject(method = "canTarget", at = @At("HEAD"), cancellable = true)
    private void noWallTarget(EntityType<?> entityType, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerRules.NO_WALL_TARGETING) return;

        MobEntity self = (MobEntity)(Object)this;

        assert target != null;
        HitResult result = self.getEntityWorld().raycast(new RaycastContext(
                self.getEyePos(),
                target.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                self
        ));

        if (result.getType() != HitResult.Type.MISS) {
            cir.setReturnValue(false);
        }
    }
}