package com.palordersoftworks.brokenstarsmpmod.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.economy.playervault.PlayerVaultPickerUi;
import com.palordersoftworks.brokenstarsmpmod.rankshop.RankShopConfig;
import com.palordersoftworks.brokenstarsmpmod.rankshop.RankShopUi;
import com.palordersoftworks.brokenstarsmpmod.config.TeamRules;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * {@code /menu} - the hub GUI linking every player-facing feature:
 * Rank Shop, Teams, Player Vaults, banknotes (withdraw/redeem) and the
 * gamemaster Sell Wand. Each button opens the feature's own GUI.
 */
public final class HubGui {
    private HubGui() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("menu")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        open(player);
                        return 1;
                    }
                    return 0;
                }));
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(27), player, false);
        gui.setTitle(Component.literal("BrokenStars Menu"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        gui.setSlot(10, new GuiElementBuilder(Items.GOLD_INGOT)
                .setName(UiHelper.name("Rank Shop", ChatFormatting.GOLD))
                .setLore(UiHelper.lore("Buy ranks with Rank Tokens.", "Convert money to tokens here."))
                .setCallback((index, clickType, input, g) -> {
                    if (RankShopConfig.RANK_SHOP_ENABLED) {
                        RankShopUi.openShop(player);
                    }
                })
                .build());

        if (TeamRules.TEAMS_ENABLED) {
            gui.setSlot(11, new GuiElementBuilder(Items.BANNER.white())
                    .setName(UiHelper.name("Teams", ChatFormatting.AQUA))
                    .setLore(UiHelper.lore("Create, join and manage teams."))
                    .setCallback((index, clickType, input, g) -> TeamsGui.open(player))
                    .build());
        }

        gui.setSlot(12, new GuiElementBuilder(Items.CHEST)
                .setName(UiHelper.name("Player Vaults", ChatFormatting.YELLOW))
                .setLore(UiHelper.lore("Open your vaults."))
                .setCallback((index, clickType, input, g) -> PlayerVaultPickerUi.open(player))
                .build());

        gui.setSlot(13, new GuiElementBuilder(Items.PAPER)
                .setName(UiHelper.name("Withdraw Banknote", ChatFormatting.WHITE))
                .setLore(UiHelper.lore("Turn your balance into a banknote."))
                .setCallback((index, clickType, input, g) -> EconomyGui.openWithdraw(player))
                .build());

        gui.setSlot(14, new GuiElementBuilder(Items.GOLD_NUGGET)
                .setName(UiHelper.name("Redeem Banknote", ChatFormatting.GREEN))
                .setLore(UiHelper.lore("Hold a banknote and click"))
                .setCallback((index, clickType, input, g) -> EconomyExtras.redeemForGui(player))
                .build());

        if (isGamemaster(player)) {
            gui.setSlot(15, new GuiElementBuilder(Items.GOLDEN_HOE)
                    .setName(UiHelper.name("Get Sell Wand", ChatFormatting.LIGHT_PURPLE))
                    .setLore(UiHelper.lore("Gamemaster tool."))
                    .setCallback((index, clickType, input, g) ->
                            EconomyExtras.giveSellWandForGui(player, player))
                    .build());
        }

        UiHelper.addCloseButton(gui);
        gui.open();
    }

    private static boolean isGamemaster(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(
                new net.minecraft.server.permissions.Permission.HasCommandLevel(
                        net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS));
    }
}
