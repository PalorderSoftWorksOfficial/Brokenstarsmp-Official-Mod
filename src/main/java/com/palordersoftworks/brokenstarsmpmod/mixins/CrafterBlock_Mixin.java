package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlock_Mixin {

    @Inject(method = "craft", at = @At("TAIL"))
    private void brokenstarsmpmod$extraCrafterConsume(BlockState state, ServerWorld world, BlockPos pos, CallbackInfo ci) {
        int ruleAmount = ServerRules.CRAFTER_CRAFT_AMOUNT;

        if (ruleAmount <= 1) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrafterBlockEntity crafterBlockEntity)) {
            return;
        }

        int extraConsume = ruleAmount - 1;

        crafterBlockEntity.getHeldStacks().forEach(stack -> {
            if (!stack.isEmpty()) {
                stack.decrement(Math.min(extraConsume, stack.getCount()));
            }
        });

        crafterBlockEntity.markDirty();
    }
}