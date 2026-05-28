package net.minecraftforge.eventbus;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public final class EventBus {
    public static final EventBus INSTANCE = new EventBus();

    public final Event<ServerLifecycleEvents.ServerStarting> SERVER_STARTING = ServerLifecycleEvents.SERVER_STARTING;
    public final Event<ServerLifecycleEvents.ServerStarted> SERVER_STARTED = ServerLifecycleEvents.SERVER_STARTED;
    public final Event<ServerLifecycleEvents.ServerStopping> SERVER_STOPPING = ServerLifecycleEvents.SERVER_STOPPING;
    public final Event<ServerLifecycleEvents.ServerStopped> SERVER_STOPPED = ServerLifecycleEvents.SERVER_STOPPED;
    public final Event<ServerLifecycleEvents.StartDataPackReload> START_DATA_PACK_RELOAD = ServerLifecycleEvents.START_DATA_PACK_RELOAD;
    public final Event<ServerLifecycleEvents.EndDataPackReload> END_DATA_PACK_RELOAD = ServerLifecycleEvents.END_DATA_PACK_RELOAD;
    public final Event<ServerLifecycleEvents.SyncDataPackContents> SYNC_DATA_PACK_CONTENTS = ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS;

    public final Event<ServerTickEvents.StartTick> START_SERVER_TICK = ServerTickEvents.START_SERVER_TICK;
    public final Event<ServerTickEvents.EndTick> END_SERVER_TICK = ServerTickEvents.END_SERVER_TICK;
    public final Event<ServerTickEvents.StartWorldTick> START_WORLD_TICK = ServerTickEvents.START_WORLD_TICK;
    public final Event<ServerTickEvents.EndWorldTick> END_WORLD_TICK = ServerTickEvents.END_WORLD_TICK;

    public final Event<ServerWorldEvents.Load> WORLD_LOAD = ServerWorldEvents.LOAD;
    public final Event<ServerWorldEvents.Unload> WORLD_UNLOAD = ServerWorldEvents.UNLOAD;

    public final Event<ServerBlockEntityEvents.Load> BLOCK_ENTITY_LOAD = ServerBlockEntityEvents.BLOCK_ENTITY_LOAD;
    public final Event<ServerBlockEntityEvents.Unload> BLOCK_ENTITY_UNLOAD = ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD;

    public final Event<ServerChunkEvents.Load> CHUNK_LOAD = ServerChunkEvents.CHUNK_LOAD;
    public final Event<ServerChunkEvents.Unload> CHUNK_UNLOAD = ServerChunkEvents.CHUNK_UNLOAD;
    public final Event<ServerChunkEvents.Generate> CHUNK_GENERATE = ServerChunkEvents.CHUNK_GENERATE;

    public final Event<ServerEntityEvents.Load> ENTITY_LOAD = ServerEntityEvents.ENTITY_LOAD;
    public final Event<ServerEntityEvents.Unload> ENTITY_UNLOAD = ServerEntityEvents.ENTITY_UNLOAD;
    public final Event<ServerEntityEvents.EquipmentChange> ENTITY_EQUIPMENT_CHANGE = ServerEntityEvents.EQUIPMENT_CHANGE;

    public final Event<ServerLivingEntityEvents.AfterDamage> LIVING_AFTER_DAMAGE = ServerLivingEntityEvents.AFTER_DAMAGE;
    public final Event<ServerLivingEntityEvents.AfterDeath> LIVING_AFTER_DEATH = ServerLivingEntityEvents.AFTER_DEATH;
    public final Event<ServerLivingEntityEvents.AllowDamage> LIVING_ALLOW_DAMAGE = ServerLivingEntityEvents.ALLOW_DAMAGE;
    public final Event<ServerLivingEntityEvents.AllowDeath> LIVING_ALLOW_DEATH = ServerLivingEntityEvents.ALLOW_DEATH;
    public final Event<ServerLivingEntityEvents.MobConversion> LIVING_MOB_CONVERSION = ServerLivingEntityEvents.MOB_CONVERSION;

    public final Event<ServerPlayerEvents.AfterRespawn> PLAYER_AFTER_RESPAWN = ServerPlayerEvents.AFTER_RESPAWN;
    public final Event<ServerPlayerEvents.CopyFrom> PLAYER_COPY_FROM = ServerPlayerEvents.COPY_FROM;
    public final Event<ServerPlayerEvents.AllowDeath> PLAYER_ALLOW_DEATH = ServerPlayerEvents.ALLOW_DEATH;

    public final Event<ServerEntityCombatEvents.AfterKilledOtherEntity> AFTER_KILLED_OTHER_ENTITY = ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY;
    public final Event<ServerEntityWorldChangeEvents.AfterEntityChange> AFTER_ENTITY_CHANGE_WORLD = ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD;
    public final Event<ServerEntityWorldChangeEvents.AfterPlayerChange> AFTER_PLAYER_CHANGE_WORLD = ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD;

    public final Event<ServerMessageEvents.AllowChatMessage> ALLOW_CHAT_MESSAGE = ServerMessageEvents.ALLOW_CHAT_MESSAGE;
    public final Event<ServerMessageEvents.AllowCommandMessage> ALLOW_COMMAND_MESSAGE = ServerMessageEvents.ALLOW_COMMAND_MESSAGE;
    public final Event<ServerMessageEvents.AllowGameMessage> ALLOW_GAME_MESSAGE = ServerMessageEvents.ALLOW_GAME_MESSAGE;
    public final Event<ServerMessageEvents.ChatMessage> CHAT_MESSAGE = ServerMessageEvents.CHAT_MESSAGE;
    public final Event<ServerMessageEvents.CommandMessage> COMMAND_MESSAGE = ServerMessageEvents.COMMAND_MESSAGE;
    public final Event<ServerMessageEvents.GameMessage> GAME_MESSAGE = ServerMessageEvents.GAME_MESSAGE;

    public final Event<AttackBlockCallback> ATTACK_BLOCK = AttackBlockCallback.EVENT;
    public final Event<AttackEntityCallback> ATTACK_ENTITY = AttackEntityCallback.EVENT;
    public final Event<PlayerBlockBreakEvents.Before> BLOCK_BREAK_BEFORE = PlayerBlockBreakEvents.BEFORE;
    public final Event<PlayerBlockBreakEvents.After> BLOCK_BREAK_AFTER = PlayerBlockBreakEvents.AFTER;
    public final Event<PlayerBlockBreakEvents.Canceled> BLOCK_BREAK_CANCELED = PlayerBlockBreakEvents.CANCELED;
    public final Event<UseBlockCallback> USE_BLOCK = UseBlockCallback.EVENT;
    public final Event<UseEntityCallback> USE_ENTITY = UseEntityCallback.EVENT;
    public final Event<UseItemCallback> USE_ITEM = UseItemCallback.EVENT;

    public final Event<EntityTrackingEvents.StartTracking> START_TRACKING = EntityTrackingEvents.START_TRACKING;
    public final Event<EntityTrackingEvents.StopTracking> STOP_TRACKING = EntityTrackingEvents.STOP_TRACKING;

    public final Event<EntitySleepEvents.AllowBed> ALLOW_BED = EntitySleepEvents.ALLOW_BED;
    public final Event<EntitySleepEvents.AllowNearbyMonsters> ALLOW_NEARBY_MONSTERS = EntitySleepEvents.ALLOW_NEARBY_MONSTERS;
    public final Event<EntitySleepEvents.AllowResettingTime> ALLOW_RESETTING_TIME = EntitySleepEvents.ALLOW_RESETTING_TIME;
    public final Event<EntitySleepEvents.AllowSettingSpawn> ALLOW_SETTING_SPAWN = EntitySleepEvents.ALLOW_SETTING_SPAWN;
    public final Event<EntitySleepEvents.AllowSleeping> ALLOW_SLEEPING = EntitySleepEvents.ALLOW_SLEEPING;
    public final Event<EntitySleepEvents.AllowSleepTime> ALLOW_SLEEP_TIME = EntitySleepEvents.ALLOW_SLEEP_TIME;
    public final Event<EntitySleepEvents.ModifySleepingDirection> MODIFY_SLEEPING_DIRECTION = EntitySleepEvents.MODIFY_SLEEPING_DIRECTION;
    public final Event<EntitySleepEvents.ModifyWakeUpPosition> MODIFY_WAKE_UP_POSITION = EntitySleepEvents.MODIFY_WAKE_UP_POSITION;
    public final Event<EntitySleepEvents.SetBedOccupationState> SET_BED_OCCUPATION_STATE = EntitySleepEvents.SET_BED_OCCUPATION_STATE;
    public final Event<EntitySleepEvents.StartSleeping> START_SLEEPING = EntitySleepEvents.START_SLEEPING;
    public final Event<EntitySleepEvents.StopSleeping> STOP_SLEEPING = EntitySleepEvents.STOP_SLEEPING;

    public final Event<EntityElytraEvents.Allow> ALLOW_ELYTRA_FLIGHT = EntityElytraEvents.ALLOW;
    public final Event<EntityElytraEvents.Custom> CUSTOM_ELYTRA_FLIGHT = EntityElytraEvents.CUSTOM;

    private EventBus() {}
}