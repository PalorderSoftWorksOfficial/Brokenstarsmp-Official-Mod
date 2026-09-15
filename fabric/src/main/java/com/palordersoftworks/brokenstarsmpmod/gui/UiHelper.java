package com.palordersoftworks.brokenstarsmpmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Shared building blocks for the player-facing chest GUIs. */
public final class UiHelper {
    private UiHelper() {}

    /** Last row, second-from-right: the Back button's slot for any GUI size. */
    public static int backSlot(int size) {
        return size - 5;
    }

    /** Bottom-right: the Close button's slot for any GUI size. */
    public static int closeSlot(int size) {
        return size - 1;
    }

    public static void fill(SimpleGui gui, Item filler) {
        ItemStack stack = new ItemStack(filler);
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, stack);
        }
    }

    public static Component name(String text, ChatFormatting... formatting) {
        return Component.literal(text).withStyle(formatting);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    public static void addBackButton(SimpleGui gui, Runnable back) {
        gui.setSlot(backSlot(gui.getSize()), new GuiElementBuilder(Items.ARROW)
                .setName(name("Back", ChatFormatting.RED))
                .setCallback((index, clickType, input, g) -> back.run())
                .build());
    }

    public static void addCloseButton(SimpleGui gui) {
        gui.setSlot(closeSlot(gui.getSize()), new GuiElementBuilder(Items.BARRIER)
                .setName(name("Close", ChatFormatting.RED))
                .setCallback((index, clickType, input, g) -> gui.getPlayer().closeContainer())
                .build());
    }

    public static int clampSize(int size) {
        int clamped = Math.max(9, Math.min(54, size));
        return clamped - clamped % 9;
    }

    public static MenuType<?> menuTypeFor(int size) {
        return switch (size / 9) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
