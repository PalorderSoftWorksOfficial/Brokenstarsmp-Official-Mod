package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlock_Mixin {

    @Inject(method = "dispenseFrom(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$craftBatch(BlockState state, ServerLevel world, BlockPos pos, CallbackInfo ci) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrafterBlockEntity crafter)) return;

        CraftingInput input = crafter.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> optional = CrafterBlock.getPotentialResults(world, input);

        if (optional.isEmpty()) {
            world.levelEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        RecipeHolder<CraftingRecipe> recipeEntry = optional.get();
        CraftingRecipe recipe = recipeEntry.value();

        int maxBatch = Math.max(1, ServerRules.CRAFTER_CRAFT_AMOUNT);

        int possibleBatches = maxBatch;
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                possibleBatches = Math.min(possibleBatches, stack.getCount());
            }
        }

        if (possibleBatches <= 0) possibleBatches = 1;

        ItemStack output = recipe.assemble(input);
        if (output.isEmpty()) {
            world.levelEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        int scaledCount = Math.min(output.getCount() * possibleBatches, output.getMaxStackSize());
        ItemStack finalOutput = output.copy();
        finalOutput.setCount(scaledCount);

        // vanilla-like state change (minimal updates)
        crafter.setCraftingTicksRemaining(6);
        world.setBlock(pos, state.setValue(CrafterBlock.CRAFTING, true), Block.UPDATE_CLIENTS);

        finalOutput.onCraftedBySystem(world);

        brokenstarsmpmod$transferOrSpawnStack(world, pos, crafter, finalOutput, state, recipeEntry);

        // consume inputs
        for (ItemStack stack : crafter.getItems()) {
            if (!stack.isEmpty()) {
                stack.shrink(Math.min(possibleBatches, stack.getCount()));
            }
        }

        crafter.setChanged();
        ci.cancel();
    }

    @Unique
    private void brokenstarsmpmod$transferOrSpawnStack(ServerLevel world, BlockPos pos,
                                                       CrafterBlockEntity blockEntity,
                                                       ItemStack stack,
                                                       BlockState state,
                                                       RecipeHolder<?> recipe) {

        Direction direction = state.getValue(CrafterBlock.ORIENTATION).front();
        Container inventory = HopperBlockEntity.getContainerAt(world, pos.relative(direction));

        ItemStack remaining = stack.copy();

        if (inventory != null && !(inventory instanceof CrafterBlockEntity)) {
            remaining = HopperBlockEntity.addItem(blockEntity, inventory, remaining, direction.getOpposite());
        }

        if (!remaining.isEmpty()) {
            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 spawnPos = center.relative(direction, 0.7);

            DefaultDispenseItemBehavior.spawnItem(world, remaining, 6, direction, spawnPos);

            for (ServerPlayer player :
                    world.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(center, 17, 17, 17))) {
                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(player, recipe.id(), blockEntity.getItems());
            }

            world.levelEvent(1049, pos, 0);
            world.levelEvent(2010, pos, direction.get3DDataValue());
        }
    }
}