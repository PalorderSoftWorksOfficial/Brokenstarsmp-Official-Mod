package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.world.World;
import net.minecraft.block.enums.Orientation;
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
        if (!(blockEntity instanceof CrafterBlockEntity crafterBlockEntity)) {
            return;
        }

        int ruleAmount = ServerRules.CRAFTER_CRAFT_AMOUNT;
        int multiplier = Math.max(1, ruleAmount);

        CraftingRecipeInput input = crafterBlockEntity.createRecipeInput();
        Optional<RecipeEntry<CraftingRecipe>> optional = CrafterBlock.getCraftingRecipe(world, input);

        if (optional.isEmpty()) {
            world.syncWorldEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        RecipeEntry<CraftingRecipe> recipeEntry = optional.get();
        ItemStack output = recipeEntry.value().craft(input, world.getRegistryManager());

        if (output.isEmpty()) {
            world.syncWorldEvent(1050, pos, 0);
            ci.cancel();
            return;
        }

        int scaledCount = output.getCount() * multiplier;
        if (scaledCount > output.getMaxCount()) {
            scaledCount = output.getMaxCount();
        }

        output = output.copy();
        output.setCount(scaledCount);

        crafterBlockEntity.setCraftingTicksRemaining(6);
        world.setBlockState(pos, state.with(CrafterBlock.CRAFTING, true), 2);
        output.onCraftByCrafter(world);
        this.brokenstarsmpmod$transferOrSpawnStack(world, pos, crafterBlockEntity, output, state, recipeEntry);

        for (ItemStack remainder : recipeEntry.value().getRecipeRemainders(input)) {
            if (!remainder.isEmpty()) {
                ItemStack scaledRemainder = remainder.copy();
                int remainderCount = scaledRemainder.getCount() * multiplier;
                if (remainderCount > scaledRemainder.getMaxCount()) {
                    remainderCount = scaledRemainder.getMaxCount();
                }
                scaledRemainder.setCount(remainderCount);
                this.brokenstarsmpmod$transferOrSpawnStack(world, pos, crafterBlockEntity, scaledRemainder, state, recipeEntry);
            }
        }

        int consumeAmount = multiplier;
        crafterBlockEntity.getHeldStacks().forEach(stack -> {
            if (!stack.isEmpty()) {
                stack.decrement(Math.min(consumeAmount, stack.getCount()));
            }
        });

        crafterBlockEntity.markDirty();
        ci.cancel();
    }

    @Unique
    private void brokenstarsmpmod$transferOrSpawnStack(ServerWorld world, BlockPos pos, CrafterBlockEntity blockEntity, ItemStack stack, BlockState state, RecipeEntry<?> recipe) {
        Direction direction = state.get(CrafterBlock.ORIENTATION).getFacing();
        Inventory inventory = HopperBlockEntity.getInventoryAt(world, pos.offset(direction));
        ItemStack itemStack = stack.copy();

        if (inventory != null && (inventory instanceof CrafterBlockEntity || stack.getCount() > inventory.getMaxCount(stack))) {
            while (!itemStack.isEmpty()) {
                ItemStack itemStack2 = itemStack.copyWithCount(1);
                ItemStack itemStack3 = HopperBlockEntity.transfer(blockEntity, inventory, itemStack2, direction.getOpposite());
                if (!itemStack3.isEmpty()) {
                    break;
                }

                itemStack.decrement(1);
            }
        } else if (inventory != null) {
            while (!itemStack.isEmpty()) {
                int i = itemStack.getCount();
                itemStack = HopperBlockEntity.transfer(blockEntity, inventory, itemStack, direction.getOpposite());
                if (i == itemStack.getCount()) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            Vec3d vec3d = Vec3d.ofCenter(pos);
            Vec3d vec3d2 = vec3d.offset(direction, 0.7);
            ItemDispenserBehavior.spawnItem(world, itemStack, 6, direction, vec3d2);

            for (ServerPlayerEntity serverPlayerEntity : world.getNonSpectatingEntities(ServerPlayerEntity.class, Box.of(vec3d, 17.0F, 17.0F, 17.0F))) {
                Criteria.CRAFTER_RECIPE_CRAFTED.trigger(serverPlayerEntity, recipe.id(), blockEntity.getHeldStacks());
            }

            world.syncWorldEvent(1049, pos, 0);
            world.syncWorldEvent(2010, pos, direction.getIndex());
        }
    }
}