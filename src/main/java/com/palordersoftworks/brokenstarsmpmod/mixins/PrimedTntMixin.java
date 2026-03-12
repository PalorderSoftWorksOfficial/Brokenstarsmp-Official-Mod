package com.palordersoftworks.brokenstarsmpmod.mixins;

import net.minecraft.entity.TntEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(TntEntity.class)
public abstract class PrimedTntMixin {

    @Unique private float palorder$damage = 4.0f;
    @Unique private double palorder$explosionRadius = 10.0;
    @Unique private float palorder$downForce = 0.04f;
    @Unique private final Map<EntityType<?>, Float> palorder$entitySpecificDamage = new HashMap<>();

    @Shadow public abstract int getFuse();

    @Inject(method = "tick", at = @At("HEAD"))
    private void palorder$applyDownForce(CallbackInfo ci) {
        TntEntity self = (TntEntity) (Object) this;
        World world = self.getEntityWorld();

        if (!world.isClient()) {
            Vec3d vel = self.getVelocity();
            self.setVelocity(vel.x, vel.y - palorder$downForce, vel.z);
        }
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void palorder$customExplosion(CallbackInfo ci) {
        TntEntity self = (TntEntity) (Object) this;
        World world = self.getEntityWorld();

        if (!world.isClient()) {
            world.createExplosion(self, self.getX(), self.getY(), self.getZ(),
                    (float) palorder$explosionRadius, World.ExplosionSourceType.TNT);

            DamageSource explosionType = Explosion.createDamageSource(world, self);

            Box area = self.getBoundingBox().expand(palorder$explosionRadius);
            for (Entity entity : world.getNonSpectatingEntities(Entity.class, area)) {
                if (entity != self) {
                    float appliedDamage = palorder$entitySpecificDamage
                            .getOrDefault(entity.getType(), palorder$damage);
                    if (entity instanceof LivingEntity) {
                        entity.damage((ServerWorld)world, explosionType, appliedDamage);
                    }
                }
            }
        }

        self.remove(Entity.RemovalReason.DISCARDED);
        ci.cancel();
    }

    @Unique
    public void palorder$setDamage(float damage) {
        this.palorder$damage = damage;
    }

    @Unique
    public void palorder$setExplosionRadius(double radius) {
        this.palorder$explosionRadius = radius;
    }

    @Unique
    public void palorder$setDownForce(float force) {
        this.palorder$downForce = force;
    }

    @Unique
    public void palorder$setDamageForEntityType(EntityType<?> type, float damage) {
        palorder$entitySpecificDamage.put(type, damage);
    }
}