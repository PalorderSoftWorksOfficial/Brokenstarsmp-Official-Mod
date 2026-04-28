package com.palordersoftworks.brokenstarsmpmod.initiaters;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.wand.SellWand;
import com.palordersoftworks.luaj.accesswidener.LuaCommands;
import com.palordersoftworks.luaj.accesswidener.LuaScriptManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.DROP_AT_FEET_RADIUS;

public class DropAtFeet implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            CobbleOreQueue.process();
        });
        ConfigManager.registerAnnotatedConfigs(ServerRules.class);
        ConfigManager.registerCommands();
        final LuaScriptManager LUA = new LuaScriptManager();
        // Drop‑at‑feet logic
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ItemEntity item)) return;
            if (!(world instanceof ServerWorld serverWorld)) return;

            double ix = item.getX();
            double iy = item.getY();
            double iz = item.getZ();
            int r = DROP_AT_FEET_RADIUS;

            for (PlayerEntity player : serverWorld.getPlayers()) {
                double dx = Math.abs(player.getX() - ix);
                double dy = Math.abs(player.getY() - iy);
                double dz = Math.abs(player.getZ() - iz);

                if (dx <= r && dy <= r && dz <= r) {
                    item.updatePosition(player.getX(), player.getY(), player.getZ());
                    item.setVelocity(0, 0, 0);
                    return;
                }
            }
        });

        // EconomyCraft events
        EconomyCraft.registerEvents();

        // PvP kill handling
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;

            LivingEntity attacker = victim.getAttacker();
            if (attacker instanceof ServerPlayerEntity killer) {
                EconomyCraft.getManager(victim.getEntityWorld().getServer()).handlePvpKill(victim, killer);
            }
        });

        // SellWand use
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (!SellWand.isSellWand(stack)) {
                return ActionResult.PASS;
            }

            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                int sold = SellWand.useOnTargetContainer(serverPlayer);
                return sold > 0 ? ActionResult.SUCCESS : ActionResult.PASS;
            }

            return ActionResult.SUCCESS;
        });
        LuaCommands.register();
    }
}