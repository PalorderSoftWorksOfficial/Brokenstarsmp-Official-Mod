package com.palordersoftworks.economycraft.playervault;

import com.mojang.authlib.GameProfile;
import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyCraft;
import com.palordersoftworks.economycraft.EconomyManager;
import com.palordersoftworks.economycraft.util.IdentityCompat;
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
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
        int maxAllowed = PlayerVaultCommands.resolveMaxVaults(player);
        if (maxAllowed <= 0) {
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
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new VaultPickerMenu(syncId, inv, (ServerPlayerEntity) p, economy, maxAllowed);
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
        private int unlockedVaults;

        VaultPickerMenu(int syncId, PlayerInventory inv, ServerPlayerEntity viewer, EconomyManager economy, int maxVaults) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.viewer = viewer;
            this.economy = economy;
            this.maxVaults = maxVaults;
            this.unlockedVaults = economy.getPlayerVaults().getUnlockedVaultCount(viewer.getUuid(), maxVaults);
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
            unlockedVaults = economy.getPlayerVaults().getUnlockedVaultCount(viewer.getUuid(), maxVaults);
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
                if (vaultIndex <= unlockedVaults) {
                    ItemStack chest = new ItemStack(Items.CHEST);
                    String vName = economy.getPlayerVaults().getVaultName(viewer.getUuid(), vaultIndex);
                    chest.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Vault #" + vaultIndex + (vName != null ? " - " + vName : ""))
                                    .styled(s -> s.withItalic(false).withColor(LABEL).withBold(true)));
                    chest.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("Click to open").styled(s -> s.withItalic(false).withColor(VALUE)),
                            Text.literal(EconomyConfig.get().playerVaultRows + " row(s)")
                                    .styled(s -> s.withItalic(false).withColor(Formatting.DARK_AQUA)))));
                    container.setStack(i, chest);
                } else {
                    ItemStack locked = new ItemStack(Items.BARRIER);
                    locked.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Vault #" + vaultIndex + " (Locked)")
                                    .styled(s -> s.withItalic(false).withColor(Formatting.RED).withBold(true)));
                    locked.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("Click Create Vault to unlock the next slot.")
                                    .styled(s -> s.withItalic(false).withColor(VALUE)))));
                    container.setStack(i, locked);
                }
            }

            container.setStack(navRowStart, createBalanceItem(viewer));

            ItemStack info = new ItemStack(Items.KNOWLEDGE_BOOK);
            info.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Vault slots").styled(s -> s.withItalic(false).withColor(Formatting.GREEN).withBold(true)));
            info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Unlocked: " + unlockedVaults + " vault(s)")
                            .styled(s -> s.withItalic(false).withColor(Formatting.YELLOW)),
                    Text.literal("Maximum: " + maxVaults + " vault(s)")
                            .styled(s -> s.withItalic(false).withColor(Formatting.WHITE)),
                    Text.literal("Extra slots are set by the server (e.g. LuckPerms meta).")
                            .styled(s -> s.withItalic(false).withColor(VALUE)))));
            container.setStack(navRowStart + 1, info);

            ItemStack createHint = new ItemStack(Items.EMERALD);
            createHint.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Create Vault").styled(s -> s.withItalic(false).withColor(Formatting.AQUA).withBold(true)));
            createHint.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal(unlockedVaults >= maxVaults
                                    ? "You already unlocked the maximum."
                                    : "Click to unlock Vault #" + (unlockedVaults + 1))
                            .styled(s -> s.withItalic(false).withColor(VALUE)))));
            container.setStack(navRowStart + 2, createHint);

        ItemStack renameHint = new ItemStack(Items.NAME_TAG);
        renameHint.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("Rename Vault").styled(s -> s.withItalic(false).withColor(Formatting.AQUA).withBold(true)));
        renameHint.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Click to choose a vault to rename").styled(s -> s.withItalic(false).withColor(VALUE))
        )));
        container.setStack(navRowStart + 7, renameHint);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }
        if (viewer.getCommandSource().hasPermissionLevel(4)) {
                ItemStack admin = new ItemStack(Items.PLAYER_HEAD);
                ProfileComponentCompat.tryResolvedOrUnresolved(viewer.getGameProfile())
                        .ifPresent(resolvable -> admin.set(DataComponentTypes.PROFILE, resolvable));
                admin.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal("Admin panel")
                                .styled(s -> s.withItalic(false)
                                        .withColor(Formatting.RED)
                                        .withBold(true)));
                admin.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("Click to manage any player's vaults")
                                .styled(s -> s.withItalic(false)
                                        .withColor(Formatting.DARK_RED))
                )));
                container.setStack(navRowStart + 6, admin);
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
                    if (vaultIndex >= 1 && vaultIndex <= unlockedVaults) {
                        if (dragType == 1) {
                            ((ServerPlayerEntity) player).closeHandledScreen();
                            PlayerVaultUi.openRenameAnvil((ServerPlayerEntity) player, economy, viewer.getUuid(), vaultIndex);
                            return;
                        }
                        ((ServerPlayerEntity) player).closeHandledScreen();
                        PlayerVaultUi.open((ServerPlayerEntity) player, economy, vaultIndex);
                        return;
                    } else if (vaultIndex > unlockedVaults && vaultIndex <= maxVaults) {
                        viewer.sendMessage(Text.literal("Vault #" + vaultIndex + " is locked. Click Create Vault first.")
                                .formatted(Formatting.YELLOW));
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
                    if (unlockedVaults >= maxVaults) {
                        viewer.sendMessage(Text.literal("You already unlocked the maximum vaults (" + maxVaults + ").")
                                .formatted(Formatting.RED));
                        return;
                    }
                    int newCount = economy.getPlayerVaults().tryUnlockNextVault(viewer.getUuid(), maxVaults);
                    viewer.sendMessage(Text.literal("Unlocked Vault #" + newCount + " (" + newCount + "/" + maxVaults + ").")
                            .formatted(Formatting.GREEN));
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 7) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    openRenamePicker((ServerPlayerEntity) player, economy, maxVaults);
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
    if (viewer.getCommandSource().hasPermissionLevel(4)
        && slot == navRowStart + 6) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    PlayerVaultAdminUi.open((ServerPlayerEntity) player, economy);
                    return;
                }
            }
            if (slot < 45 && type == SlotActionType.QUICK_MOVE) {
                int vaultIndex = page * 45 + slot + 1;
                if (vaultIndex >= 1 && vaultIndex <= unlockedVaults) {
                    economy.getPlayerVaults().clearVault(viewer.getUuid(), vaultIndex);
                    viewer.sendMessage(Text.literal("Vault #" + vaultIndex + " cleared.").formatted(Formatting.GREEN));
                    updatePage();
                    return;
                }
            }
            if (slot < 45 && type == SlotActionType.THROW) {
                int vaultIndex = page * 45 + slot + 1;
                if (vaultIndex >= 1 && vaultIndex <= unlockedVaults) {
                    economy.getPlayerVaults().deleteVault(viewer.getUuid(), vaultIndex);
                    viewer.sendMessage(Text.literal("Vault #" + vaultIndex + " deleted.").formatted(Formatting.RED));
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

    /** Opens a small menu listing vault indexes so the player can pick one to rename. */
    static void openRenamePicker(ServerPlayerEntity player, EconomyManager economy, int maxVaults) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Choose vault to rename");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new RenamePickerMenu(syncId, inv, (ServerPlayerEntity) p, economy, maxVaults);
            }
        });
    }

    private static final class RenamePickerMenu extends ScreenHandler {
        private final ServerPlayerEntity viewer;
        private final EconomyManager economy;
        private final int maxVaults;
        private final SimpleInventory container = new SimpleInventory(54);
        private final int navRowStart = 45;

        RenamePickerMenu(int syncId, PlayerInventory inv, ServerPlayerEntity viewer, EconomyManager economy, int maxVaults) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.viewer = viewer;
            this.economy = economy;
            this.maxVaults = maxVaults;
            updatePage();

            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                    @Override public boolean canInsert(ItemStack stack) { return false; }
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
            for (int i = 0; i < 45; i++) {
                int vaultIndex = i + 1;
                if (vaultIndex > maxVaults) {
                    ItemStack barrier = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                    barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").styled(s -> s.withItalic(false)));
                    container.setStack(i, barrier);
                    continue;
                }
                int unlocked = economy.getPlayerVaults().getUnlockedVaultCount(viewer.getUuid(), maxVaults);
                if (vaultIndex <= unlocked) {
                    ItemStack paper = new ItemStack(Items.PAPER);
                    paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Vault #" + vaultIndex).styled(s -> s.withItalic(false).withColor(Formatting.GOLD)));
                    paper.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("Click to rename this vault").styled(s -> s.withItalic(false).withColor(VALUE))
                    )));
                    container.setStack(i, paper);
                } else {
                    ItemStack locked = new ItemStack(Items.BARRIER);
                    locked.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Vault #" + vaultIndex + " (Locked)").styled(s -> s.withItalic(false).withColor(Formatting.RED)));
                    container.setStack(i, locked);
                }
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP && slot < 45) {
                int vaultIndex = slot + 1;
                int unlocked = economy.getPlayerVaults().getUnlockedVaultCount(viewer.getUuid(), maxVaults);
                if (vaultIndex >= 1 && vaultIndex <= unlocked) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    PlayerVaultUi.openRenameAnvil((ServerPlayerEntity) player, economy, viewer.getUuid(), vaultIndex);
                    return;
                } else {
                    viewer.sendMessage(Text.literal("Vault #" + vaultIndex + " is locked.").formatted(Formatting.YELLOW));
                    return;
                }
            }
            super.onSlotClick(slot, dragType, type, player);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
    }
}
