package com.palordersoftworks.brokenstarsmpmod.economy;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class BanknoteUtil {
    public record ParsedNote(long amount, String signature) {}

    private BanknoteUtil() {}

    public static ItemStack createNote(long amount) {
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(amount + " Money Note")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true))
        );

        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("economyBanknote", true);
        nbt.putString("currency", "money");
        nbt.putLong("amount", amount);
        nbt.putString("signature", UUID.randomUUID().toString());
        note.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return note;
    }

    public static ParsedNote parse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PAPER)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag nbt = data.copyTag();
        if (!nbt.getBooleanOr("economyBanknote", false)) return null;
        long amount = nbt.getLongOr("amount", 0L);
        String signature = nbt.getStringOr("signature", "");
        if (amount <= 0 || signature.isBlank()) return null;
        return new ParsedNote(amount, signature);
    }
}
