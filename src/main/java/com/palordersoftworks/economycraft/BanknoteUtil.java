package com.palordersoftworks.economycraft;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class BanknoteUtil {
    public enum Currency {
        MONEY("money", "Money"),
        SHARDS("shards", "Shards");

        private final String id;
        private final String display;

        Currency(String id, String display) {
            this.id = id;
            this.display = display;
        }

        public String id() { return id; }
        public String display() { return display; }

        public static Currency fromInput(String raw) {
            if (raw == null) return null;
            String n = raw.trim().toLowerCase();
            return switch (n) {
                case "money", "cash", "balance", "bal" -> MONEY;
                case "shards", "shard" -> SHARDS;
                default -> null;
            };
        }
    }

    public record ParsedNote(Currency currency, long amount, String signature) {}

    private BanknoteUtil() {}

    public static ItemStack createNote(long amount, Currency currency) {
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(amount + " " + currency.display() + " Note")
                        .styled(s -> s.withItalic(false).withColor(Formatting.GOLD).withBold(true)));

        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("economyBanknote", true);
        nbt.putString("currency", currency.id());
        nbt.putLong("amount", amount);
        nbt.putString("signature", UUID.randomUUID().toString());
        note.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return note;
    }

    public static ParsedNote parse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.PAPER)) return null;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        NbtCompound nbt = data.copyNbt();
        if (!nbt.getBoolean("economyBanknote", false)) return null;
        String currencyRaw = nbt.getString("currency", "");
        Currency currency = Currency.fromInput(currencyRaw);
        long amount = nbt.getLong("amount", 0L);
        String signature = nbt.getString("signature", "");
        if (currency == null || amount <= 0 || signature.isBlank()) return null;
        return new ParsedNote(currency, amount, signature);
    }
}
