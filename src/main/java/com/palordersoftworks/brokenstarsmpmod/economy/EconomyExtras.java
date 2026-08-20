package com.palordersoftworks.brokenstarsmpmod.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.palordersoftworks.brokenstarsmpmod.economy.playervault.PlayerVaultCommands;
import com.palordersoftworks.brokenstarsmpmod.economy.playervault.PlayerVaultManager;
import com.palordersoftworks.brokenstarsmpmod.economy.wand.SellWand;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class EconomyExtras {
    private static volatile PlayerVaultManager vaults;
    private static volatile BanknoteStore banknotes;
    private static volatile MinecraftServer boundServer;

    private EconomyExtras() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EconomyExtrasConfig.load(server);
            boundServer = server;
            vaults = new PlayerVaultManager(server);
            banknotes = new BanknoteStore(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (vaults != null) vaults.save();
            if (banknotes != null) banknotes.save();
            EconomyExtrasConfig.save(server);
            vaults = null;
            banknotes = null;
            boundServer = null;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PlayerVaultCommands.register(dispatcher);

            dispatcher.register(Commands.literal("withdraw")
                    .requires(src -> EconomyExtrasConfig.get().banknotesEnabled && src.getPlayer() != null)
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> withdraw(
                                    ctx.getSource().getPlayerOrException(),
                                    LongArgumentType.getLong(ctx, "amount")
                            ))));

            dispatcher.register(Commands.literal("redeem")
                    .requires(src -> EconomyExtrasConfig.get().banknotesEnabled && src.getPlayer() != null)
                    .executes(ctx -> redeem(ctx.getSource().getPlayerOrException())));

            dispatcher.register(Commands.literal("givesellwand")
                    .requires(PermissionCompat.gamemaster())
                    .executes(ctx -> giveSellWand(ctx.getSource().getPlayerOrException(), ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> giveSellWand(
                                    EntityArgument.getPlayer(ctx, "player"),
                                    ctx.getSource().getPlayer()
                            ))));
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);

            if (EconomyExtrasConfig.get().sellWandEnabled && SellWand.isSellWand(stack)) {
                int sold = SellWand.useOnTargetContainer(serverPlayer);
                return sold > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }

            if (EconomyExtrasConfig.get().banknotesEnabled && BanknoteUtil.parse(stack) != null) {
                if (redeem(serverPlayer) > 0) {
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        });
    }

    public static PlayerVaultManager getVaults(MinecraftServer server) {
        if (vaults == null || boundServer != server) {
            throw new IllegalStateException("Player vaults are not ready");
        }
        return vaults;
    }

    public static BanknoteStore getBanknotes(MinecraftServer server) {
        if (banknotes == null || boundServer != server) {
            throw new IllegalStateException("Banknote store is not ready");
        }
        return banknotes;
    }

    private static int withdraw(ServerPlayer player, long amount) {
        if (!EconomyExtrasConfig.get().banknotesEnabled) {
            player.sendSystemMessage(Component.literal("Banknotes are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        if (!eco.removeMoney(player.getUUID(), amount)) {
            player.sendSystemMessage(Component.literal("Not enough balance.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack note = BanknoteUtil.createNote(amount);
        if (!player.getInventory().add(note)) {
            player.drop(note, false);
        }
        player.sendSystemMessage(Component.literal(
                "Withdrew " + EconomyCraft.formatMoney(amount) + " as a banknote.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int redeem(ServerPlayer player) {
        if (!EconomyExtrasConfig.get().banknotesEnabled) {
            player.sendSystemMessage(Component.literal("Banknotes are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack hand = player.getMainHandItem();
        BanknoteUtil.ParsedNote note = BanknoteUtil.parse(hand);
        if (note == null) {
            player.sendSystemMessage(Component.literal("Hold a valid banknote in your main hand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        BanknoteStore store = getBanknotes(player.level().getServer());
        if (!store.markRedeemed(note.signature())) {
            player.sendSystemMessage(Component.literal(
                    "This banknote has already been redeemed (duped). Please notify staff.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyCraft.getManager(player.level().getServer()).addMoney(player.getUUID(), note.amount());
        hand.shrink(1);
        player.sendSystemMessage(Component.literal(
                "Redeemed " + EconomyCraft.formatMoney(note.amount()) + ".")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int giveSellWand(ServerPlayer target, ServerPlayer actor) {
        ItemStack wand = SellWand.createSellWandItem();
        if (!target.getInventory().add(wand)) {
            target.drop(wand, false);
        }
        target.sendSystemMessage(Component.literal("You received a Sell Wand.").withStyle(ChatFormatting.GOLD));
        if (actor != null && actor != target) {
            actor.sendSystemMessage(Component.literal("Gave a Sell Wand to " + target.getName().getString() + ".")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }
}
