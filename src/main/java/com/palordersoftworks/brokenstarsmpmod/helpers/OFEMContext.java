package com.palordersoftworks.brokenstarsmpmod.helpers;

import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;

/**
 * Context object for Optimized Fast Entity Movement.
 * Stores the axis, movement delta, bounding box, entity, and world.
 */
public class OFEMContext {

    public Direction.Axis axis;
    public double movementOnAxis;
    public Box entityBoundingBox;
    public Entity entity;
    public World world;

    public OFEMContext(World world, Entity entity) {
        this.world = world;
        this.entity = entity;
        this.entityBoundingBox = entity.getBoundingBox();
    }

    public OFEMContext(World world, Entity entity, Box boundingBox) {
        this.world = world;
        this.entity = entity;
        this.entityBoundingBox = boundingBox;
    }
}