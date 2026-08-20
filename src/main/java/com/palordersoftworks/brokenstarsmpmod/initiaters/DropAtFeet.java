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
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.unstablesmp.UnstableSMPFeatures;
import com.palordersoftworks.luaj.accesswidener.LuaCommands;
import com.palordersoftworks.luaj.accesswidener.LuaScriptManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.palordersoftworks.brokenstarsmpmod.config.ServerRules.DROP_AT_FEET_RADIUS;


public class DropAtFeet implements ModInitializer {

    public static final List<ScheduledTask> SCHEDULED_TASKS = new ArrayList<>();
    public static long serverTick;


    @Override
    public void onInitialize() {

        ServerTickEvents.END_LEVEL_TICK.register(world -> CobbleOreQueue.process());

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


        ServerLifecycleEvents.SERVER_STARTED.register(
                TranslationProbeController::init
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(
                TranslationProbeController::onServerStopping
        );

        ServerTickEvents.END_SERVER_TICK.register(
                TranslationProbeController::tick
        );


        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TranslationProbeController.onPlayerJoin(
                        handler.player,
                        server
                )
        );


        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TranslationProbeController.clearPlayer(
                        handler.player.getUUID(),
                        server
                )
        );


        final LuaScriptManager LUA = new LuaScriptManager();



        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {

            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) return;

            if (!(world instanceof ServerLevel serverWorld)) return;


            double ix = item.getX();
            double iy = item.getY();
            double iz = item.getZ();

            int r = DROP_AT_FEET_RADIUS;


            for (Player player : serverWorld.players()) {

                double dx = Math.abs(player.getX() - ix);
                double dy = Math.abs(player.getY() - iy);
                double dz = Math.abs(player.getZ() - iz);


                if (dx <= r && dy <= r && dz <= r) {

                    item.absSnapTo(
                            player.getX(),
                            player.getY(),
                            player.getZ()
                    );

                    item.setDeltaMovement(
                            0,
                            0,
                            0
                    );

                    return;
                }
            }
        });



        registerVoidRod();



        EconomyExtras.register();
        LuaCommands.register();
    }
    private static void registerVoidRod() {

        UseItemCallback.EVENT.register((player, world, hand) -> {

            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }


            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }


            ItemStack stack = player.getItemInHand(hand);


            if (!(stack.getItem() instanceof FishingRodItem)) {
                return InteractionResult.PASS;
            }


            CustomData customData =
                    stack.get(DataComponents.CUSTOM_DATA);


            if (customData == null || customData.isEmpty()) {
                return InteractionResult.PASS;
            }


            CompoundTag nbt = customData.copyTag();


            if (!"void".equals(
                    nbt.getString("RodType")
            )) {
                return InteractionResult.PASS;
            }


            if (!serverPlayer.getStringUUID().equals(
                    nbt.getString("Voidrodowner")
            )) {
                return InteractionResult.PASS;
            }



            int rodUse =
                    nbt.getIntOr("RodUse", 0) + 1;


            nbt.putInt(
                    "RodUse",
                    rodUse
            );


            CustomData.set(
                    DataComponents.CUSTOM_DATA,
                    stack,
                    nbt
            );



            if (rodUse == 1) {

                runLater(130, () -> {

                    if (!serverPlayer.isAlive()) {
                        return;
                    }


                    ItemStack held =
                            serverPlayer.getItemInHand(hand);


                    CustomData heldData =
                            held.get(DataComponents.CUSTOM_DATA);


                    if (heldData == null || heldData.isEmpty()) {
                        return;
                    }


                    CompoundTag heldNbt =
                            heldData.copyTag();


                    if (!"void".equals(
                            heldNbt.getString("RodType")
                    )) {
                        return;
                    }


                    if (!serverPlayer.getStringUUID().equals(
                            heldNbt.getString("Voidrodowner")
                    )) {
                        return;
                    }


                    heldNbt.putInt(
                            "RodUse",
                            0
                    );


                    CustomData.set(
                            DataComponents.CUSTOM_DATA,
                            held,
                            heldNbt
                    );
                });


                return InteractionResult.PASS;
            }



            if (rodUse < 2) {
                return InteractionResult.PASS;
            }



            FishingHook hook =
                    serverPlayer.fishing;


            if (hook == null) {

                nbt.putInt(
                        "RodUse",
                        0
                );

                CustomData.set(
                        DataComponents.CUSTOM_DATA,
                        stack,
                        nbt
                );

                return InteractionResult.PASS;
            }



            Entity hooked =
                    hook.getHookedIn();



            if (!(hooked instanceof ServerPlayer target)) {

                nbt.putInt(
                        "RodUse",
                        0
                );

                CustomData.set(
                        DataComponents.CUSTOM_DATA,
                        stack,
                        nbt
                );

                return InteractionResult.PASS;
            }



            runLater(20, () -> {

                if (!target.isAlive()) {
                    return;
                }


                target.teleportTo(
                        target.level(),
                        target.getX(),
                        -64.0,
                        target.getZ(),
                        Set.<Relative>of(),
                        target.getViewYRot(1.0F),
                        target.getViewXRot(1.0F),
                        false
                );
            });



            nbt.putInt(
                    "RodUse",
                    0
            );


            CustomData.set(
                    DataComponents.CUSTOM_DATA,
                    stack,
                    nbt
            );


            return InteractionResult.PASS;
        });
    }



    public static void runLater(int delayTicks, Runnable action) {

        SCHEDULED_TASKS.add(
                new ScheduledTask(
                        serverTick + delayTicks,
                        action
                )
        );
    }



    private static void tickScheduledTasks() {

        Iterator<ScheduledTask> iterator =
                SCHEDULED_TASKS.iterator();


        while (iterator.hasNext()) {

            ScheduledTask task =
                    iterator.next();


            if (task.executeAt > serverTick) {
                continue;
            }


            iterator.remove();


            task.action.run();
        }
    }



    public record ScheduledTask(
            long executeAt,
            Runnable action
    ) {
    }
}