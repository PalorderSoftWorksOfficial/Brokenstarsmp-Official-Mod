package com.palordersoftworks.brokenstarsmpmod.fakes;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;

public interface RedstoneWireBlockInterface {
    BlockState updateLogicPublic(World world, BlockPos pos, BlockState state);

    void setWiresGivePower(boolean givePower);

    boolean getWiresGivePower();
}
