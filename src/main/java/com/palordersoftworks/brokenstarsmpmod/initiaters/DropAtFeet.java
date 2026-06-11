package com.palordersoftworks.brokenstarsmpmod.initiaters;

import com.palordersoftworks.brokenstarsmpmod.commands.AptCommand;
import com.palordersoftworks.brokenstarsmpmod.commands.ImmortalCommand;
import com.palordersoftworks.brokenstarsmpmod.commands.LinkFishingRod;
import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.config.UnstableSMPRules;
import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeCommands;
import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeController;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.luaj.accesswidener.LuaCommands;
import com.palordersoftworks.luaj.accesswidener.LuaScriptManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.DROP_AT_FEET_RADIUS;

public class DropAtFeet implements ModInitializer {

    public static final List<ScheduledTask> SCHEDULED_TASKS = new ArrayList<>();
    public static long serverTick;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> CobbleOreQueue.process());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTick++;
            tickScheduledTasks();
        });

        ConfigManager.registerAnnotatedConfigs(ServerRules.class);
        ConfigManager.registerAnnotatedConfigs(UnstableSMPRules.class);
        ConfigManager.registerCommands();
        TranslationProbeCommands.register();
        UnstableSMPFeatures.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LinkFishingRod.register(dispatcher, registryAccess, environment);
            AptCommand.register(dispatcher, registryAccess, environment);
            ImmortalCommand.register(dispatcher, registryAccess, environment);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(TranslationProbeController::init);
        ServerLifecycleEvents.SERVER_STOPPING.register(TranslationProbeController::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(TranslationProbeController::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TranslationProbeController.onPlayerJoin(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TranslationProbeController.clearPlayer(handler.player.getUuid(), server));

        final LuaScriptManager LUA = new LuaScriptManager();

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof net.minecraft.entity.ItemEntity item)) return;
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

        EconomyCraft.registerEvents();

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;

            LivingEntity attacker = victim.getAttacker();
            if (attacker instanceof ServerPlayerEntity killer) {
                EconomyCraft.getManager(victim.getEntityWorld().getServer()).handlePvpKill(victim, killer);
            }
        });

        LuaCommands.register();
    }

    private static void tickScheduledTasks() {
        Iterator<ScheduledTask> it = SCHEDULED_TASKS.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (task.executeAt > serverTick) continue;
            it.remove();
            task.action.run();
        }
    }

    public record ScheduledTask(long executeAt, Runnable action) {
    }
}
