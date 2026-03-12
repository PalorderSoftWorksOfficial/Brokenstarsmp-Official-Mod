package com.palordersoftworks.brokenstarsmpmod.helpers;

import com.palordersoftworks.brokenstarsmpmod.helpers.UpdateNodeType.UpdateNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

public class UpdateNode {
    public BlockState currentState;
    public UpdateNode[] neighborNodes;
    public BlockPos self;
    public BlockPos parent;
    public UpdateNodeType.UpdateNodeTypes type = UpdateNodeType.UpdateNodeTypes.UNKNOWN;
    public int layer;
    public boolean visited;
    public int xbias;
    public int zbias;
}