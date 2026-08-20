package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.initiaters.DropAtFeet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
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
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/FishingHook;retrieve(Lnet/minecraft/world/item/ItemStack;)I"
            )
    )
    private void brokenstarsmp$stasis(
            Level world,
            net.minecraft.world.entity.player.Player user,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (world.isClientSide()) return;
        if (!(user instanceof ServerPlayer serverPlayer)) return;

        ItemStack stack = user.getItemInHand(hand);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag nbt = customData.copyTag();
        if (!"void".equals(nbt.getStringOr("RodType", ""))) return;

        String owner = nbt.getStringOr("Voidrodowner", "");
        boolean linkedOwner = serverPlayer.getStringUUID().equals(owner);

        if (!linkedOwner) {
            voidEntity(serverPlayer);
            return;
        }

        FishingHook hook = serverPlayer.fishing;
        if (hook == null) return;

        Entity hooked = hook.getHookedIn();
        if (hooked == null) return;

        runLater(1, () -> voidEntity(hooked));
    }

    @Unique
    private static void voidEntity(Entity entity) {
        entity.teleportTo(entity.getX(), -70.0, entity.getZ());
    }
    @Unique
    private static void runLater(int delayTicks, Runnable action) {
        SCHEDULED_TASKS.add(new DropAtFeet.ScheduledTask(serverTick + delayTicks, action));
    }
}