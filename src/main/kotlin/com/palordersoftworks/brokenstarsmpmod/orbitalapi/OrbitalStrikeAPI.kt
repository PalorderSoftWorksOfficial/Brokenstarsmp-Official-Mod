package com.palordersoftworks.brokenstarsmpmod.orbitalapi

import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.TntEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object OrbitalStrikeAPI {
    private val RANDOM = Random()

    fun summonNuke(world: ServerWorld, centerX: Int, centerY: Int, centerZ: Int, numRings: Int?) {
        val baseFuse = 80
        val gravity = -0.03
        val velocityMultiplier = 1.4
        val baseRadii = intArrayOf(12, 22, 32, 42, 52, 62, 72, 82, 92, 102)

        val rings = numRings ?: 10
        val radii = if (rings <= baseRadii.size) baseRadii.copyOf(rings) else IntArray(rings)

        if (rings > baseRadii.size) {
            System.arraycopy(baseRadii, 0, radii, 0, baseRadii.size)
            for (i in baseRadii.size until rings) {
                radii[i] = 98 + (i - baseRadii.size + 1) * 10
            }
        }

        val centerTnt = TntEntity(world, centerX + 0.5, centerY.toDouble(), centerZ + 0.5, null)
        centerTnt.setFuse(baseFuse)
        centerTnt.setVelocity(0.0, gravity, 0.0)
        world.spawnEntity(centerTnt)

        for (ringIndex in radii.indices) {
            val r = radii[ringIndex]
            val tntsPerRing = when {
                ringIndex == 0 -> 84
                ringIndex == 1 -> 50
                ringIndex in 2..5 -> 70
                ringIndex == 6 -> 90
                else -> min(100, Math.round(r * 2 - ringIndex * 1.2f))
            }
            for (i in 0 until tntsPerRing) {
                val angle = Math.random() * Math.PI * 2.0
                val dx = cos(angle)
                val dz = sin(angle)
                val vx = dx * (r / baseFuse.toDouble()) * velocityMultiplier
                val vz = dz * (r / baseFuse.toDouble()) * velocityMultiplier
                val tnt = TntEntity(world, centerX + 0.5, centerY.toDouble(), centerZ + 0.5, null)
                tnt.setFuse(baseFuse)
                tnt.setVelocity(vx, gravity, vz)
                world.spawnEntity(tnt)
            }
        }
    }

    fun summonStab(world: ServerWorld, centerX: Int, centerZ: Int, groundY: Int) {
        val minY = world.getBottomY()
        val maxY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ)
        for (y in maxY downTo minY) {
            val pos = BlockPos(centerX, y, centerZ)
            if (!world.getBlockState(pos).isAir) {
                for (j in 0..1) {
                    val tnt = TntEntity(world, centerX + 0.5, y.toDouble(), centerZ + 0.5, null)
                    tnt.setFuse(0)
                    world.spawnEntity(tnt)
                }
            }
        }
    }

    fun summonWolves(player: PlayerEntity, amount: Int) {
        val world: World = player.entityWorld
        val px = player.x
        val py = player.y
        val pz = player.z
        for (i in 0 until amount) {
            val wolf = WolfEntity(EntityType.WOLF, world)
            wolf.refreshPositionAndAngles(px, py, pz, player.yaw, player.pitch)
            wolf.setOwner(player)
            wolf.setTamed(true, true)
            wolf.equipStack(EquipmentSlot.CHEST, ItemStack(Items.LEATHER_CHESTPLATE))
            wolf.addStatusEffect(StatusEffectInstance(StatusEffects.SPEED, 3600, 1))
            wolf.addStatusEffect(StatusEffectInstance(StatusEffects.STRENGTH, 3600, 1))
            wolf.addStatusEffect(StatusEffectInstance(StatusEffects.REGENERATION, 9600, 0))
            val angle = Math.random() * Math.PI * 2.0
            val pitch = (Math.random() - 0.5) * Math.PI * 0.5
            val horizontalSpeed = 0.8
            val vx = cos(angle) * cos(pitch) * horizontalSpeed
            val vy = sin(pitch) * 0.6 + 0.3
            val vz = sin(angle) * cos(pitch) * horizontalSpeed
            wolf.setVelocity(vx, vy, vz)
            world.spawnEntity(wolf)
        }
    }

    fun teleportStasis(player: PlayerEntity, x: Int, y: Int, z: Int) {
        val world = player.entityWorld as ServerWorld
        val cx = player.x
        val cy = player.y
        val cz = player.z
        world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f)
        world.spawnParticles(ParticleTypes.EXPLOSION, cx, cy + 1.0, cz, 50, 0.5, 0.5, 0.5, 0.5)
        player.requestTeleport(x + 0.5, y.toDouble(), z + 0.5)
        world.playSound(null, x + 0.5, y.toDouble(), z + 0.5, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f)
        world.spawnParticles(ParticleTypes.EXPLOSION, x + 0.5, y + 1.0, z + 0.5, 50, 0.5, 0.5, 0.5, 0.5)
    }

    fun fireStraightRailgun(player: PlayerEntity, target: Location, spreadRadius: Double) {
        val world = player.entityWorld as ServerWorld
        val playerEye = player.getCameraPosVec(1.0f)
        val direction = Vec3d(target.x - playerEye.x, target.y - playerEye.y, target.z - playerEye.z).normalize()
        val velocity = 500.0
        for (i in 0..44) {
            val offsetX = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0
            val offsetY = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0
            val offsetZ = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0
            val spawnPos = playerEye.add(direction.multiply(3.0)).add(offsetX, offsetY, offsetZ)
            val arrow = ArrowEntity(EntityType.ARROW, world)
            arrow.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0.0f, 0.0f)
            arrow.velocity = direction.multiply(velocity)
            arrow.setOwner(player)
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED
            world.spawnEntity(arrow)
        }
    }
    fun fireOrbitalRailgun(world: ServerWorld, target: Location, arrows: Int, height: Int, spread: Double) {
        val spawnX = target.x
        val spawnZ = target.z
        val spawnY = target.y + height.toDouble()

        for (i in 0 until arrows) {
            val offsetX = (RANDOM.nextDouble() - 0.5) * spread
            val offsetZ = (RANDOM.nextDouble() - 0.5) * spread

            val arrow = ArrowEntity(EntityType.ARROW, world)
            arrow.refreshPositionAndAngles(
                spawnX + offsetX,
                spawnY,
                spawnZ + offsetZ,
                0.0f,
                0.0f
            )

            val dirX = (target.x - (spawnX + offsetX)) * 0.01
            val dirZ = (target.z - (spawnZ + offsetZ)) * 0.01
            val dirY = -1.0

            val velocityVector = Vec3d(dirX, dirY, dirZ).normalize().multiply(200.0)
            arrow.setVelocity(velocityVector)

            arrow.setOwner(null)
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED
            world.spawnEntity(arrow)
        }
    }
    class Location(var x: Double, var y: Double, var z: Double)
}