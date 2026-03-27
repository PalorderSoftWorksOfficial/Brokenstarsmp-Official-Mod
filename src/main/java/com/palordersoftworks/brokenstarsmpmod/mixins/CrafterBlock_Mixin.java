package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlock_Mixin {

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$craftBatch(BlockState state, ServerWorld world, BlockPos pos, CallbackInfo ci) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrafterBlockEntity crafter)) return;

        CraftingRecipeInput input = crafter.createRecipeInput();
        Optional<RecipeEntry<CraftingRecipe>> optional = CrafterBlock.getCraftingRecipe(world, input);
        if (optional.isEmpty()) {
            world.syncWorldEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        RecipeEntry<CraftingRecipe> recipeEntry = optional.get();
        CraftingRecipe recipe = recipeEntry.value();

        int maxBatch = Math.max(1, ServerRules.CRAFTER_CRAFT_AMOUNT);

        int possibleBatches = maxBatch;
        for (ItemStack stack : input.getStacks()) {
            if (!stack.isEmpty()) {
                possibleBatches = Math.min(possibleBatches, stack.getCount());
            }
        }

        if (possibleBatches <= 0) {
            possibleBatches = 1;
        }

        ItemStack output = recipe.craft(input, world.getRegistryManager());
        if (output.isEmpty()) {
            world.syncWorldEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        int scaledCount = Math.min(output.getCount() * possibleBatches, output.getMaxCount());
        output = output.copy();
        output.setCount(scaledCount);

        crafter.setCraftingTicksRemaining(6);
        world.setBlockState(pos, state.with(CrafterBlock.CRAFTING, true), 2);
        output.onCraftByCrafter(world);

        brokenstarsmpmod$transferOrSpawnStack(world, pos, crafter, output, state, recipeEntry);

        for (ItemStack stack : crafter.getHeldStacks()) {
            if (!stack.isEmpty()) {
                stack.decrement(Math.min(possibleBatches, stack.getCount()));
            }
        }

        crafter.markDirty();
        ci.cancel();
    }

    @Unique
    private void brokenstarsmpmod$transferOrSpawnStack(ServerWorld world, BlockPos pos, CrafterBlockEntity blockEntity,
                                                       ItemStack stack, BlockState state, RecipeEntry<?> recipe) {
        Direction direction = state.get(CrafterBlock.ORIENTATION).getFacing();
        Inventory inventory = HopperBlockEntity.getInventoryAt(world, pos.offset(direction));

        ItemStack toTransfer = stack.copy();

        if (inventory != null && !(inventory instanceof CrafterBlockEntity)) {
            while (!toTransfer.isEmpty()) {
                int prevCount = toTransfer.getCount();
                toTransfer = HopperBlockEntity.transfer(blockEntity, inventory, toTransfer, direction.getOpposite());
                if (prevCount == toTransfer.getCount()) break;
            }
        }

        if (!toTransfer.isEmpty()) {
            Vec3d center = Vec3d.ofCenter(pos);
            Vec3d spawnPos = center.offset(direction, 0.7);
            ItemDispenserBehavior.spawnItem(world, toTransfer, 6, direction, spawnPos);

            for (ServerPlayerEntity player : world.getNonSpectatingEntities(ServerPlayerEntity.class, Box.of(center, 17, 17, 17))) {
                Criteria.CRAFTER_RECIPE_CRAFTED.trigger(player, recipe.id(), blockEntity.getHeldStacks());
            }

            world.syncWorldEvent(1049, pos, 0);
            world.syncWorldEvent(2010, pos, direction.getIndex());
        }
    }
}