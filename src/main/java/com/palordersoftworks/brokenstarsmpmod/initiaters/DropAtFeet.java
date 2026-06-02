package com.palordersoftworks.brokenstarsmpmod.initiaters;

import com.palordersoftworks.brokenstarsmpmod.commands.AptCommand;
import com.palordersoftworks.brokenstarsmpmod.commands.LinkFishingRod;
import com.palordersoftworks.brokenstarsmpmod.config.ConfigManager;
import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import com.palordersoftworks.brokenstarsmpmod.fluid.CobbleOreQueue;
import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeCommands;
import com.palordersoftworks.brokenstarsmpmod.translationprobe.TranslationProbeController;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.luaj.accesswidener.LuaCommands;
import com.palordersoftworks.luaj.accesswidener.LuaScriptManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.DROP_AT_FEET_RADIUS;

public class DropAtFeet implements ModInitializer {

    private static final List<ScheduledTask> SCHEDULED_TASKS = new ArrayList<>();
    private static long serverTick;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> CobbleOreQueue.process());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTick++;
            tickScheduledTasks();
        });

        ConfigManager.registerAnnotatedConfigs(ServerRules.class);
        ConfigManager.registerCommands();
        TranslationProbeCommands.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LinkFishingRod.register(dispatcher, registryAccess, environment);
            AptCommand.register(dispatcher, registryAccess, environment);
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

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof FishingRodItem)) return ActionResult.PASS;

            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData == null || customData.isEmpty()) return ActionResult.PASS;

            NbtCompound nbt = customData.copyNbt();
            if (!"void".equals(nbt.getString("RodType"))) return ActionResult.PASS;
            if (!serverPlayer.getUuidAsString().equals(nbt.getString("Voidrodowner"))) return ActionResult.PASS;

            int rodUse = nbt.getInt("RodUse",0) + 1;
            nbt.putInt("RodUse", rodUse);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt);

            if (rodUse == 1) {
                runLater(130, () -> {
                    if (!serverPlayer.isAlive()) return;
                    ItemStack held = serverPlayer.getStackInHand(hand);
                    NbtComponent heldData = held.get(DataComponentTypes.CUSTOM_DATA);
                    if (heldData == null || heldData.isEmpty()) return;

                    NbtCompound heldNbt = heldData.copyNbt();
                    if (!"void".equals(heldNbt.getString("RodType"))) return;
                    if (!serverPlayer.getUuidAsString().equals(heldNbt.getString("Voidrodowner"))) return;

                    heldNbt.putInt("RodUse", 0);
                    NbtComponent.set(DataComponentTypes.CUSTOM_DATA, held, heldNbt);
                });
                return ActionResult.PASS;
            }

            if (rodUse < 2) return ActionResult.PASS;

            FishingBobberEntity hook = serverPlayer.fishHook;
            if (hook == null) {
                nbt.putInt("RodUse", 0);
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt);
                return ActionResult.PASS;
            }

            Entity hooked = hook.getHookedEntity();
            if (!(hooked instanceof ServerPlayerEntity target)) {
                nbt.putInt("RodUse", 0);
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt);
                return ActionResult.PASS;
            }

            runLater(20, () -> {
                if (!target.isAlive()) return;
                target.teleport(
                        target.getEntityWorld(),
                        target.getX(),
                        -64.0,
                        target.getZ(),
                        Set.<PositionFlag>of(),
                        target.getYaw(1.0F),
                        target.getPitch(1.0F),
                        false
                );
            });

            nbt.putInt("RodUse", 0);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt);
            return ActionResult.PASS;
        });

        LuaCommands.register();
    }

    private static void runLater(int delayTicks, Runnable action) {
        SCHEDULED_TASKS.add(new ScheduledTask(serverTick + delayTicks, action));
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

    private record ScheduledTask(long executeAt, Runnable action) {
    }
}