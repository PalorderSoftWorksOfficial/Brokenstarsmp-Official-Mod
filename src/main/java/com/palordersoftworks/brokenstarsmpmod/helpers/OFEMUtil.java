package com.palordersoftworks.brokenstarsmpmod.helpers;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Utility class for OFEM (Fabric port)
 */
public class OFEMUtil {

    /**
     * Creates a new OFEMContext for the given entity.
     */
    public static OFEMContext createContext(World world, Entity entity, Vec3d movement) {
        return new OFEMContext(world, entity);
    }

    /**
     * Creates a new OFEMContext. Always returns context for "always-on" behavior.
     */
    public static OFEMContext checkAndCreateContext(World world, Entity entity, Box movement) {
        return new OFEMContext(world, entity);
    }

    /**
     * Returns only the block collisions along the movement axis.
     * Vanilla block collision shapes are filtered to the axis the entity is moving on.
     */
    public static List<VoxelShape> getAxisOnlyBlockCollision(OFEMContext ctx) {
        if (ctx == null || ctx.axis == null) return Collections.emptyList();

        List<VoxelShape> collisions = new ArrayList<>();
        Box box = ctx.entityBoundingBox;

        // expand slightly in case entity is touching blocks
        Box searchBox = box.expand(0.001);

        BlockPos min = new BlockPos((int) searchBox.minX, (int) searchBox.minY, (int) searchBox.minZ);
        BlockPos max = new BlockPos((int) searchBox.maxX, (int) searchBox.maxY, (int) searchBox.maxZ);

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = ctx.world.getBlockState(pos);
                    if (!state.isAir()) {
                        VoxelShape shape = state.getCollisionShape(ctx.world, pos);
                        if (!shape.isEmpty()) {
                            // Filter shape along axis
                            if (ctx.axis == Direction.Axis.X && shape.getMax(Direction.Axis.X) <= box.minX) continue;
                            if (ctx.axis == Direction.Axis.Y && shape.getMax(Direction.Axis.Y) <= box.minY) continue;
                            if (ctx.axis == Direction.Axis.Z && shape.getMax(Direction.Axis.Z) <= box.minZ) continue;
                            collisions.add(shape);
                        }
                    }
                }
            }
        }

        return collisions;
    }

    /**
     * For Lithium compat: fetch block collisions via vanilla getter but filter for axis.
     */
    public static Iterable<VoxelShape> getAxisOnlyBlockCollision(OFEMContext ctx,
                                                                 BiFunction<World, Entity, Iterable<VoxelShape>> vanillaGetter) {
        Iterable<VoxelShape> shapes = vanillaGetter.apply(ctx.world, ctx.entity);
        List<VoxelShape> filtered = new ArrayList<>();

        for (VoxelShape shape : shapes) {
            if (ctx.axis == Direction.Axis.X && shape.getMax(Direction.Axis.X) <= ctx.entityBoundingBox.minX) continue;
            if (ctx.axis == Direction.Axis.Y && shape.getMax(Direction.Axis.Y) <= ctx.entityBoundingBox.minY) continue;
            if (ctx.axis == Direction.Axis.Z && shape.getMax(Direction.Axis.Z) <= ctx.entityBoundingBox.minZ) continue;
            filtered.add(shape);
        }

        return filtered;
    }
}