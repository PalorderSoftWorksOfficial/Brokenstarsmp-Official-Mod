package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobEntity_TargetMixin {

    @Shadow
    private @Nullable LivingEntity target;

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void noWallTarget(EntityType<?> entityType, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerRules.NO_WALL_TARGETING || target == null) return;

        Mob self = (Mob) (Object) this;
        HitResult result = self.level().clip(new ClipContext(
                self.getEyePosition(),
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                self
        ));

        if (result.getType() != HitResult.Type.MISS) {
            cir.setReturnValue(false);
        }
    }
}
