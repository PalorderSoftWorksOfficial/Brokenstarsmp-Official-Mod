package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.commands.PermissionUtil;
import com.palordersoftworks.brokenstarsmpmod.initiaters.DropAtFeet;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.palordersoftworks.brokenstarsmpmod.initiaters.DropAtFeet.SCHEDULED_TASKS;
import static com.palordersoftworks.brokenstarsmpmod.initiaters.DropAtFeet.serverTick;

@Mixin(FishingRodItem.class)
public abstract class FishingRodItem_Mixin {

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/FishingBobberEntity;use(Lnet/minecraft/item/ItemStack;)I"
            )
    )
    private void brokenstarsmp$stasis(
            World world,
            net.minecraft.entity.player.PlayerEntity user,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()) return;
        if (!(user instanceof ServerPlayerEntity serverPlayer)) return;

        ItemStack stack = user.getStackInHand(hand);
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return;

        NbtCompound nbt = customData.copyNbt();
        if (!"void".equals(nbt.getString("RodType", ""))) return;

        String owner = nbt.getString("Voidrodowner", "");
        boolean linkedOwner = serverPlayer.getUuidAsString().equals(owner);
        boolean protectedUser = PermissionUtil.isProtected(serverPlayer);

        if (!linkedOwner && !protectedUser) {
            voidEntity(serverPlayer);
            return;
        }

        FishingBobberEntity hook = serverPlayer.fishHook;
        if (hook == null) return;

        Entity hooked = hook.getHookedEntity();
        if (hooked == null) return;

        if (hooked instanceof ServerPlayerEntity target && PermissionUtil.isProtected(target)) {
            return;
        }

        runLater(1, () -> voidEntity(hooked));
    }

    @Unique
    private static void voidEntity(Entity entity) {
        entity.requestTeleport(entity.getX(), -70.0, entity.getZ());
    }
    @Unique
    private static void runLater(int delayTicks, Runnable action) {
        SCHEDULED_TASKS.add(new DropAtFeet.ScheduledTask(serverTick + delayTicks, action));
    }
}