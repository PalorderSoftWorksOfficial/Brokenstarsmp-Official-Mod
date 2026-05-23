package net.minecraftforge.eventbus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.Event;

@Environment(EnvType.CLIENT)
public final class EventBusClient {
    public static final EventBusClient INSTANCE = new EventBusClient();

    public final Event<ClientLifecycleEvents.ClientStarted> CLIENT_STARTED = ClientLifecycleEvents.CLIENT_STARTED;
    public final Event<ClientLifecycleEvents.ClientStopping> CLIENT_STOPPING = ClientLifecycleEvents.CLIENT_STOPPING;

    public final Event<ClientTickEvents.StartTick> START_CLIENT_TICK = ClientTickEvents.START_CLIENT_TICK;
    public final Event<ClientTickEvents.EndTick> END_CLIENT_TICK = ClientTickEvents.END_CLIENT_TICK;
    public final Event<ClientTickEvents.StartWorldTick> START_WORLD_TICK = ClientTickEvents.START_WORLD_TICK;
    public final Event<ClientTickEvents.EndWorldTick> END_WORLD_TICK = ClientTickEvents.END_WORLD_TICK;

    public final Event<ClientWorldEvents.AfterClientWorldChange> AFTER_CLIENT_WORLD_CHANGE = ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE;

    public final Event<ClientBlockEntityEvents.Load> BLOCK_ENTITY_LOAD = ClientBlockEntityEvents.BLOCK_ENTITY_LOAD;
    public final Event<ClientBlockEntityEvents.Unload> BLOCK_ENTITY_UNLOAD = ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD;

    public final Event<ClientChunkEvents.Load> CHUNK_LOAD = ClientChunkEvents.CHUNK_LOAD;
    public final Event<ClientChunkEvents.Unload> CHUNK_UNLOAD = ClientChunkEvents.CHUNK_UNLOAD;

    public final Event<ClientEntityEvents.Load> ENTITY_LOAD = ClientEntityEvents.ENTITY_LOAD;
    public final Event<ClientEntityEvents.Unload> ENTITY_UNLOAD = ClientEntityEvents.ENTITY_UNLOAD;

    public final Event<ClientCommandRegistrationCallback> CLIENT_COMMAND_REGISTRATION = ClientCommandRegistrationCallback.EVENT;

    public final Event<ClientLoginConnectionEvents.Init> LOGIN_INIT = ClientLoginConnectionEvents.INIT;
    public final Event<ClientLoginConnectionEvents.QueryStart> LOGIN_QUERY_START = ClientLoginConnectionEvents.QUERY_START;
    public final Event<ClientLoginConnectionEvents.Disconnect> LOGIN_DISCONNECT = ClientLoginConnectionEvents.DISCONNECT;

    public final Event<ClientConfigurationConnectionEvents.Init> CONFIG_INIT = ClientConfigurationConnectionEvents.INIT;
    public final Event<ClientConfigurationConnectionEvents.Ready> CONFIG_READY = ClientConfigurationConnectionEvents.READY;
    public final Event<ClientConfigurationConnectionEvents.Disconnect> CONFIG_DISCONNECT = ClientConfigurationConnectionEvents.DISCONNECT;

    public final Event<ClientPlayConnectionEvents.Init> PLAY_INIT = ClientPlayConnectionEvents.INIT;
    public final Event<ClientPlayConnectionEvents.Join> PLAY_JOIN = ClientPlayConnectionEvents.JOIN;
    public final Event<ClientPlayConnectionEvents.Disconnect> PLAY_DISCONNECT = ClientPlayConnectionEvents.DISCONNECT;

    public final Event<ClientSendMessageEvents.AllowChat> ALLOW_CHAT_SEND = ClientSendMessageEvents.ALLOW_CHAT;
    public final Event<ClientSendMessageEvents.AllowCommand> ALLOW_COMMAND_SEND = ClientSendMessageEvents.ALLOW_COMMAND;
    public final Event<ClientSendMessageEvents.Chat> CHAT_SEND = ClientSendMessageEvents.CHAT;
    public final Event<ClientSendMessageEvents.Command> COMMAND_SEND = ClientSendMessageEvents.COMMAND;
    public final Event<ClientSendMessageEvents.ChatCanceled> CHAT_SEND_CANCELED = ClientSendMessageEvents.CHAT_CANCELED;
    public final Event<ClientSendMessageEvents.CommandCanceled> COMMAND_SEND_CANCELED = ClientSendMessageEvents.COMMAND_CANCELED;
    public final Event<ClientSendMessageEvents.ModifyChat> MODIFY_CHAT_SEND = ClientSendMessageEvents.MODIFY_CHAT;
    public final Event<ClientSendMessageEvents.ModifyCommand> MODIFY_COMMAND_SEND = ClientSendMessageEvents.MODIFY_COMMAND;

    public final Event<ClientReceiveMessageEvents.AllowChat> ALLOW_CHAT_RECEIVE = ClientReceiveMessageEvents.ALLOW_CHAT;
    public final Event<ClientReceiveMessageEvents.AllowGame> ALLOW_GAME_RECEIVE = ClientReceiveMessageEvents.ALLOW_GAME;
    public final Event<ClientReceiveMessageEvents.Chat> CHAT_RECEIVE = ClientReceiveMessageEvents.CHAT;
    public final Event<ClientReceiveMessageEvents.Game> GAME_RECEIVE = ClientReceiveMessageEvents.GAME;
    public final Event<ClientReceiveMessageEvents.ChatCanceled> CHAT_RECEIVE_CANCELED = ClientReceiveMessageEvents.CHAT_CANCELED;
    public final Event<ClientReceiveMessageEvents.GameCanceled> GAME_RECEIVE_CANCELED = ClientReceiveMessageEvents.GAME_CANCELED;
    public final Event<ClientReceiveMessageEvents.ModifyGame> MODIFY_GAME_RECEIVE = ClientReceiveMessageEvents.MODIFY_GAME;

    public final Event<ItemTooltipCallback> ITEM_TOOLTIP = ItemTooltipCallback.EVENT;

    private EventBusClient() {}
}