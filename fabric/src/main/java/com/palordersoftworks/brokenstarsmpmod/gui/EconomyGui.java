package com.palordersoftworks.brokenstarsmpmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Chest GUIs for the EconomyExtras features: banknote {@code /withdraw} with a
 * preset + custom amount picker, and {@code /givesellwand} with a target picker.
 *
 * <p>Amounts are always re-derived from the clicked preset or typed number on the
 * server; the GUI never carries a trusted value into the transaction.
 */
public final class EconomyGui {
    private EconomyGui() {}

    private static final long[] WITHDRAW_PRESETS = {1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L, 10_000_000_000L};
    private static final int[] WITHDRAW_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16};

    public static void openWithdraw(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(27), player, false);
        gui.setTitle(Component.literal("Withdraw Banknote"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        long balance = com.palordersoftworks.brokenstarsmpmod.api.BrokenStarsApi
                .money(player.level().getServer(), player.getUUID());

        for (int i = 0; i < WITHDRAW_PRESETS.length; i++) {
            long amount = WITHDRAW_PRESETS[i];
            gui.setSlot(WITHDRAW_SLOTS[i], new GuiElementBuilder(Items.PAPER)
                    .setName(Component.literal(com.reazip.economycraft.EconomyCraft.formatMoney(amount))
                            .withStyle(ChatFormatting.GOLD))
                    .setLore(UiHelper.lore(
                            "Your balance: " + com.reazip.economycraft.EconomyCraft.formatMoney(balance),
                            "Click to withdraw"))
                    .setCallback((index, clickType, input, g) ->
                            com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras
                                    .withdrawForGui(player, amount))
                    .build());
        }

        gui.setSlot(22, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(UiHelper.name("Custom Amount", ChatFormatting.AQUA))
                .setLore(UiHelper.lore("Type an amount in the anvil."))
                .setCallback((index, clickType, input, g) -> openWithdrawCustom(player))
                .build());

        UiHelper.addCloseButton(gui);
        gui.open();
    }

    private static void openWithdrawCustom(ServerPlayer player) {
        AnvilInputGui gui = new AnvilInputGui(player, false) {
            @Override
            public void onInput(String input) {
                this.close();
                com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras
                        .withdrawForGui(player, parseAmount(input));
            }
        };
        gui.setTitle(Component.literal("Amount to withdraw:"));
        gui.open();
    }

    private static long parseAmount(String input) {
        try {
            return Long.parseLong(input.trim().replace(",", "").replace("_", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
