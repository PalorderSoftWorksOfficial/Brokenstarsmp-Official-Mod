package com.palordersoftworks.economycraft.playervault;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
import com.mojang.authlib.GameProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Paged chest GUI to pick a vault (like the auction house), or read vault slot limits.
 */
public final class PlayerVaultPickerUi {
    private static final Formatting LABEL = Formatting.GOLD;
    private static final Formatting VALUE = Formatting.GRAY;

    private PlayerVaultPickerUi() {}

    public static void open(ServerPlayerEntity player, EconomyManager economy) {
        int max = PlayerVaultCommands.resolveMaxVaults(player);
        if (max <= 0) {
            player.sendMessage(Text.literal("Player vaults are disabled for you.").formatted(Formatting.RED));
            return;
        }
        Text title = Text.literal("Vaults");
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new VaultPickerMenu(syncId, inv, (ServerPlayerEntity) p, economy, max);
            }
        });
    }

    private static ItemStack createBalanceItem(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = player.getGameProfile();
        ProfileComponentCompat.tryResolvedOrUnresolved(profile).ifPresent(resolvable ->
                head.set(DataComponentTypes.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.getEntityWorld().getServer()).getBalance(player.getUuid(), true);
        head.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(IdentityCompat.of(player).name()).styled(s -> s.withItalic(false).withColor(Formatting.YELLOW)));
        head.set(DataComponentTypes.LORE,
                new LoreComponent(List.of(
                        Text.literal("Balance: ").styled(s -> s.withItalic(false).withColor(Formatting.GOLD))
                                .append(Text.literal(EconomyCraft.formatMoney(balance))
                                        .styled(s -> s.withItalic(false).withColor(Formatting.DARK_PURPLE))))));
        return head;
    }

    private static final class VaultPickerMenu extends ScreenHandler {
        private final ServerPlayerEntity viewer;
        private final EconomyManager economy;
        private final int maxVaults;
        private final SimpleInventory container = new SimpleInventory(54);
        private int page;
        private final int navRowStart = 45;

        VaultPickerMenu(int syncId, PlayerInventory inv, ServerPlayerEntity viewer, EconomyManager economy, int maxVaults) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.viewer = viewer;
            this.economy = economy;
            this.maxVaults = maxVaults;
            updatePage();
            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return false;
                    }

                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }
                });
            }
            int y = 18 + 6 * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            container.clear();
            int start = page * 45;
            int totalPages = (int) Math.ceil(maxVaults / 45.0);

            for (int i = 0; i < 45; i++) {
                int vaultIndex = start + i + 1;
                if (vaultIndex > maxVaults) {
                    ItemStack barrier = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                    barrier.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal(" ").styled(s -> s.withItalic(false)));
                    container.setStack(i, barrier);
                    continue;
                }
                ItemStack chest = new ItemStack(Items.CHEST);
                chest.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal("Vault #" + vaultIndex).styled(s -> s.withItalic(false).withColor(LABEL).withBold(true)));
                chest.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("Click to open").styled(s -> s.withItalic(false).withColor(VALUE)),
                        Text.literal(EconomyConfig.get().playerVaultRows + " row(s)")
                                .styled(s -> s.withItalic(false).withColor(Formatting.DARK_AQUA)))));
                container.setStack(i, chest);
            }

            container.setStack(navRowStart, createBalanceItem(viewer));

            ItemStack info = new ItemStack(Items.KNOWLEDGE_BOOK);
            info.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Vault slots").styled(s -> s.withItalic(false).withColor(Formatting.GREEN).withBold(true)));
            info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Maximum: " + maxVaults + " vault(s)")
                            .styled(s -> s.withItalic(false).withColor(Formatting.WHITE)),
                    Text.literal("Extra slots are set by the server (e.g. LuckPerms meta).")
                            .styled(s -> s.withItalic(false).withColor(VALUE)))));
            container.setStack(navRowStart + 1, info);

            ItemStack createHint = new ItemStack(Items.EMERALD);
            createHint.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Need more vaults?").styled(s -> s.withItalic(false).withColor(Formatting.AQUA).withBold(true)));
            createHint.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Ask an admin to raise your vault limit.")
                            .styled(s -> s.withItalic(false).withColor(VALUE)))));
            container.setStack(navRowStart + 2, createHint);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages))
                            .styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);

            if (start + 45 < maxVaults) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int vaultIndex = page * 45 + slot + 1;
                    if (vaultIndex >= 1 && vaultIndex <= maxVaults) {
                        ((ServerPlayerEntity) player).closeHandledScreen();
                        PlayerVaultUi.open((ServerPlayerEntity) player, economy, vaultIndex);
                        return;
                    }
                }
                if (slot == navRowStart + 1) {
                    viewer.sendMessage(Text.literal("Your vault limit is " + maxVaults
                            + ". Admins can raise it via permissions / LuckPerms meta (see config).")
                            .formatted(Formatting.GREEN));
                    return;
                }
                if (slot == navRowStart + 2) {
                    viewer.sendMessage(Text.literal("Extra vault slots are granted by the server (permissions).")
                            .formatted(Formatting.YELLOW));
                    return;
                }
                if (slot == navRowStart + 3 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 5 && (page + 1) * 45 < maxVaults) {
                    page++;
                    updatePage();
                    return;
                }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
