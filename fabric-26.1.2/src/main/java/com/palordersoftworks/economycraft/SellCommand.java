package com.palordersoftworks.economycraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;

import java.util.ArrayList;
import java.util.List;

public final class SellCommand {

    private SellCommand() {}

    public static LiteralArgumentBuilder<ServerCommandSource> register() {
        return CommandManager.literal("sell")
                .executes(ctx -> openSellGui(ctx.getSource()))
                .then(CommandManager.literal("everything").executes(ctx -> sellEverything(ctx.getSource())));
    }

    private static int openSellGui(ServerCommandSource source) {
        if (!EconomyConfig.get().sellEnabled) {
            source.sendError(Text.literal("Selling is disabled.").formatted(Formatting.RED));
            return 0;
        }

        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Sell Items");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new SellMenu(syncId, inv, player);
            }
        });

        return 1;
    }

    private static int sellEverything(ServerCommandSource source) {
        if (!EconomyConfig.get().sellEnabled) {
            source.sendError(Text.literal("Selling is disabled.").formatted(Formatting.RED));
            return 0;
        }

        ServerPlayerEntity player = getPlayer(source);
        if (player == null) return 0;

        EconomyManager manager = EconomyCraft.getManager(player.getEntityWorld().getServer());
        PriceRegistry prices = manager.getPrices();
        PlayerInventory inventory = player.getInventory();

        long total = 0;
        List<Integer> soldSlots = new ArrayList<>();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            long value = getStackTotalValue(prices, stack);
            if (value > 0) {
                total += value;
                soldSlots.add(i);
            }
        }

        if (total <= 0) {
            player.sendMessage(Text.literal("No sellable items.").formatted(Formatting.RED));
            return 0;
        }

        if (EconomyConfig.get().dailySellLimit > 0 && !manager.tryRecordDailySell(player.getUuid(), total)) {
            handleDailyLimitFailure(manager, player);
            return 0;
        }

        for (int slot : soldSlots) {
            inventory.setStack(slot, ItemStack.EMPTY);
        }

        manager.addMoney(player.getUuid(), total);
        player.sendMessage(Text.literal("Sold for " + EconomyCraft.formatMoney(total) + ".").formatted(Formatting.GREEN));
        return 1;
    }

    private static class SellMenu extends ScreenHandler {
        private final ServerPlayerEntity player;
        private final SimpleInventory container = new SimpleInventory(27);

        protected SellMenu(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player) {
            super(ScreenHandlerType.GENERIC_9X3, syncId);
            this.player = player;

            for (int i = 0; i < 27; i++) {
                int x = 8 + (i % 9) * 18;
                int y = 18 + (i / 9) * 18;
                this.addSlot(new Slot(container, i, x, y));
            }

            int y = 18 + 3 * 18 + 14;

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(playerInventory, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }

            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInventory, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void onClosed(PlayerEntity playerEntity) {
            super.onClosed(playerEntity);

            if (!(playerEntity instanceof ServerPlayerEntity sp)) return;

            EconomyManager manager = EconomyCraft.getManager(sp.getEntityWorld().getServer());
            PriceRegistry prices = manager.getPrices();

            long total = 0;

            for (int i = 0; i < container.size(); i++) {
                ItemStack stack = container.getStack(i);
                if (stack.isEmpty()) continue;

                long value = getStackTotalValue(prices, stack);
                if (value > 0) {
                    total += value;
                } else {
                    sp.getInventory().offerOrDrop(stack);
                }
            }

            if (total > 0) {
                if (EconomyConfig.get().dailySellLimit > 0 &&
                        !manager.tryRecordDailySell(sp.getUuid(), total)) {
                    handleDailyLimitFailure(manager, sp);
                    return;
                }

                manager.addMoney(sp.getUuid(), total);
                sp.sendMessage(Text.literal("Sold for " + EconomyCraft.formatMoney(total) + ".")
                        .formatted(Formatting.GREEN));
            } else {
                sp.sendMessage(Text.literal("No sellable items.").formatted(Formatting.RED));
            }
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            return ItemStack.EMPTY;
        }
    }

    public static long getStackTotalValue(PriceRegistry prices, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (prices.isSellBlockedByDamage(stack)) return 0;

        long container = getContainerSellValue(prices, stack);
        if (container > 0) return container * stack.getCount();

        Long price = prices.getUnitSell(stack);
        if (price == null) return 0;

        return price * stack.getCount();
    }

    private static long getContainerSellValue(PriceRegistry prices, ItemStack stack) {
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        if (contents == null) return 0;

        long total = 0;
        for (ItemStack inner : contents.streamNonEmpty().toList()) {
            if (prices.isSellBlockedByDamage(inner)) continue;

            long innerContainer = getContainerSellValue(prices, inner);
            if (innerContainer > 0) {
                total += innerContainer;
                continue;
            }

            Long price = prices.getUnitSell(inner);
            if (price != null) {
                total += price * inner.getCount();
            }
        }
        return total;
    }

    private static ServerPlayerEntity getPlayer(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command.")
                    .formatted(Formatting.RED));
            return null;
        }
    }

    private static void handleDailyLimitFailure(EconomyManager manager, ServerPlayerEntity player) {
        long remaining = manager.getDailySellRemaining(player.getUuid());
        long limit = EconomyConfig.get().dailySellLimit;

        if (remaining <= 0) {
            player.sendMessage(Text.literal("Daily sell limit of " +
                    EconomyCraft.formatMoney(limit) +
                    " reached.").formatted(Formatting.RED));
        } else {
            player.sendMessage(Text.literal("You can only sell " +
                    EconomyCraft.formatMoney(remaining) +
                    " more today.").formatted(Formatting.RED));
        }
    }
}
