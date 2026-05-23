package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(ServerWorld.class)
public abstract class ServerWorld_EntityProcessingLimit_Mixin {

    @Unique
    private long brokenstarsmpmod$lastResetTick = Long.MIN_VALUE;

    @Unique
    private int brokenstarsmpmod$totalProcessedThisTick = 0;

    @Unique
    private final int[] brokenstarsmpmod$slotCounts = new int[10];

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmpmod$limitEntityProcessing(Entity entity, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        long currentTick = world.getTime();

        if (currentTick != brokenstarsmpmod$lastResetTick) {
            brokenstarsmpmod$lastResetTick = currentTick;
            brokenstarsmpmod$totalProcessedThisTick = 0;
            Arrays.fill(brokenstarsmpmod$slotCounts, 0);
        }

        int globalBudget = ServerRules.ENTITY_TICK_BUDGET;
        if (globalBudget >= 0 && brokenstarsmpmod$totalProcessedThisTick >= globalBudget) {
            ci.cancel();
            return;
        }

        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) {
            brokenstarsmpmod$totalProcessedThisTick++;
            return;
        }

        String entityId = id.toString();

        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT, 0, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING2,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT2, 1, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING3,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT3, 2, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING4,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT4, 3, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING5,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT5, 4, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING6,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT6, 5, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING7,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT7, 6, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING8,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT8, 7, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING9,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT9, 8, ci)) return;
        if (brokenstarsmpmod$checkSlot(entityId,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING10,
                ServerRules.ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT10, 9, ci)) return;

        brokenstarsmpmod$totalProcessedThisTick++;
    }

    @Unique
    private boolean brokenstarsmpmod$checkSlot(
            String entityId,
            String configuredType,
            int configuredLimit,
            int slot,
            CallbackInfo ci
    ) {
        if (configuredType == null || configuredType.isEmpty()) {
            return false;
        }

        if (!configuredType.equals(entityId)) {
            return false;
        }

        if (configuredLimit < 0) {
            return false;
        }

        if (brokenstarsmpmod$slotCounts[slot] >= configuredLimit) {
            ci.cancel();
            return true;
        }

        brokenstarsmpmod$slotCounts[slot]++;
        return false;
    }
}