package com.palordersoftworks.economycraft.playervault;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
// IdentityCompat not needed in this class; was unused import removed
import com.palordersoftworks.economycraft.util.ProfileComponentCompat;
import com.mojang.authlib.GameProfile;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerVaultAdminUi {
    private static final Formatting LABEL = Formatting.GOLD;
    private static final Formatting VALUE = Formatting.GRAY;

    private PlayerVaultAdminUi() {}

    public static void open(ServerPlayerEntity admin, EconomyManager economy) {
        admin.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Player Vault Admin");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                return new PlayerSelectionMenu(syncId, inv, economy);
            }
        });
    }

    private static class PlayerSelectionMenu extends ScreenHandler {
    // admin reference removed — not needed inside this menu xD
        private final EconomyManager economy;
        private final List<UUID> players;
        private final SimpleInventory container = new SimpleInventory(54);
        private int page;
        private final int navRowStart = 45;

        PlayerSelectionMenu(int syncId, PlayerInventory inv, EconomyManager economy) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.economy = economy;
            this.players = new ArrayList<>(economy.getPlayerVaults().getTrackedPlayers());
            this.updatePage();

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
            int totalPages = (int) Math.ceil(players.size() / 45.0);
            int start = page * 45;

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= players.size()) break;
                UUID uuid = players.get(idx);
                String name = economy.getBestName(uuid);

                ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                GameProfile profile = new GameProfile(uuid, name);
                ProfileComponentCompat.tryResolvedOrUnresolved(profile).ifPresent(resolvable -> head.set(DataComponentTypes.PROFILE, resolvable));
                head.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal(name).styled(s -> s.withItalic(false).withColor(LABEL).withBold(true)));

                int unlocked = economy.getPlayerVaults().getUnlockedVaultCount(uuid, EconomyConfig.get().playerVaultMaxAmount);
                int max = Math.max(unlocked, EconomyConfig.get().playerVaultDefaultAmount);
                head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("Unlocked: " + unlocked).styled(s -> s.withItalic(false).withColor(VALUE)),
                        Text.literal("Max: " + max).styled(s -> s.withItalic(false).withColor(Formatting.WHITE))
                )));

                container.setStack(i, head);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Back to My Vaults").styled(s -> s.withItalic(false).withColor(Formatting.YELLOW).withBold(true)));
            container.setStack(navRowStart, back);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }
            if ((page + 1) * 45 < players.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int idx = page * 45 + slot;
                    if (idx < players.size()) {
                        UUID target = players.get(idx);
                        ((ServerPlayerEntity) player).closeHandledScreen();
                        PlayerVaultAdminUi.openTarget((ServerPlayerEntity) player, economy, target);
                        return;
                    }
                }
                if (slot == navRowStart) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    PlayerVaultPickerUi.open((ServerPlayerEntity) player, economy);
                    return;
                }
                if (slot == navRowStart + 3 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 5 && (page + 1) * 45 < players.size()) {
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

    static void openTarget(ServerPlayerEntity admin, EconomyManager economy, UUID target) {
        String targetName = economy.getBestName(target);
        admin.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Vaults for " + targetName);
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                return new TargetVaultMenu(syncId, inv, admin, economy, target, targetName);
            }
        });
    }

    private static class TargetVaultMenu extends ScreenHandler {
        private final ServerPlayerEntity admin;
        private final EconomyManager economy;
        private final UUID target;
        private final String targetName;
        private final SimpleInventory container = new SimpleInventory(54);
        private int page;
        private final int navRowStart = 45;

        TargetVaultMenu(int syncId, PlayerInventory inv, ServerPlayerEntity admin, EconomyManager economy, UUID target, String targetName) {
            super(ScreenHandlerType.GENERIC_9X6, syncId);
            this.admin = admin;
            this.economy = economy;
            this.target = target;
            this.targetName = targetName;
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
            int unlocked = economy.getPlayerVaults().getUnlockedVaultCount(target, EconomyConfig.get().playerVaultMaxAmount);
            int maxVaults = Math.max(unlocked, EconomyConfig.get().playerVaultDefaultAmount);
            int totalPages = (int) Math.ceil(maxVaults / 45.0);
            int start = page * 45;

            for (int i = 0; i < 45; i++) {
                int vaultIndex = start + i + 1;
                if (vaultIndex > maxVaults) break;
                boolean isUnlocked = vaultIndex <= unlocked;
                ItemStack item;
                if (isUnlocked) {
                    item = new ItemStack(Items.CHEST);
                    String vName = economy.getPlayerVaults().getVaultName(target, vaultIndex);
                    item.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("#" + vaultIndex + (vName != null ? " - " + vName : ""))
                                    .styled(s -> s.withItalic(false).withColor(LABEL).withBold(true)));
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Click to open vault as admin").styled(s -> s.withItalic(false).withColor(VALUE)),
                Text.literal("Shift-click to clear, drop-click to delete").styled(s -> s.withItalic(false).withColor(Formatting.DARK_AQUA))
            )));
                } else {
                    item = new ItemStack(Items.BARRIER);
                    item.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Vault #" + vaultIndex + " (Locked)").styled(s -> s.withItalic(false).withColor(Formatting.RED).withBold(true)));
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Locked for player").styled(s -> s.withItalic(false).withColor(VALUE))
            )));
                }
                container.setStack(i, item);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Back to Admin Player List").styled(s -> s.withItalic(false).withColor(Formatting.YELLOW).withBold(true)));
            container.setStack(navRowStart, back);

            ItemStack clearAll = new ItemStack(Items.BUCKET);
            clearAll.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Clear all vaults").styled(s -> s.withItalic(false).withColor(Formatting.AQUA)));
        clearAll.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Click to empty all vaults for " + targetName).styled(s -> s.withItalic(false).withColor(VALUE))
        )));
            container.setStack(navRowStart + 1, clearAll);

            ItemStack deleteAll = new ItemStack(Items.TNT);
            deleteAll.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Delete all vaults").styled(s -> s.withItalic(false).withColor(Formatting.RED)));
        deleteAll.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Click to remove all vault data for " + targetName).styled(s -> s.withItalic(false).withColor(VALUE))
        )));
            container.setStack(navRowStart + 2, deleteAll);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Page " + (page + 1) + "/" + Math.max(1, totalPages)).styled(s -> s.withItalic(false)));
            container.setStack(navRowStart + 4, paper);

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 3, prev);
            }
            if ((page + 1) * 45 < maxVaults) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page").styled(s -> s.withItalic(false)));
                container.setStack(navRowStart + 5, next);
            }
        }

        @Override
        public void onSlotClick(int slot, int dragType, SlotActionType type, PlayerEntity player) {
            if (type == SlotActionType.PICKUP) {
                if (slot < 45) {
                    int vaultIndex = page * 45 + slot + 1;
                    int unlocked = economy.getPlayerVaults().getUnlockedVaultCount(target, EconomyConfig.get().playerVaultMaxAmount);
                    int maxVaults = Math.max(unlocked, EconomyConfig.get().playerVaultDefaultAmount);
                    if (vaultIndex >= 1 && vaultIndex <= maxVaults) {
                        if (vaultIndex <= unlocked) {
                            if (dragType == 1) {
                                ((ServerPlayerEntity) player).closeHandledScreen();
                                PlayerVaultUi.openRenameAnvil((ServerPlayerEntity) player, economy, target, vaultIndex);
                                return;
                            }

                            if (type == SlotActionType.QUICK_MOVE) {
                                economy.getPlayerVaults().clearVault(target, vaultIndex);
                                admin.sendMessage(Text.literal("Cleared vault #" + vaultIndex + " for " + targetName).formatted(Formatting.GREEN));
                                updatePage();
                                return;
                            }

                            if (type == SlotActionType.THROW) {
                                economy.getPlayerVaults().deleteVault(target, vaultIndex);
                                admin.sendMessage(Text.literal("Deleted vault #" + vaultIndex + " for " + targetName).formatted(Formatting.RED));
                                updatePage();
                                return;
                            }

                            ((ServerPlayerEntity) player).closeHandledScreen();
                            PlayerVaultUi.openFor((ServerPlayerEntity) player, economy, target, vaultIndex);
                            return;
                        }
                        admin.sendMessage(Text.literal("Vault #" + vaultIndex + " is locked for " + targetName).formatted(Formatting.YELLOW));
                        return;
                    }
                }

                if (slot == navRowStart) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    PlayerVaultAdminUi.open((ServerPlayerEntity) player, economy);
                    return;
                }

                if (slot == navRowStart + 1) {
                    economy.getPlayerVaults().clearAllVaults(target);
                    admin.sendMessage(Text.literal("Cleared all vaults for " + targetName).formatted(Formatting.GREEN));
                    updatePage();
                    return;
                }

                if (slot == navRowStart + 2) {
                    economy.getPlayerVaults().removePlayer(target);
                    admin.sendMessage(Text.literal("Removed all vault records for " + targetName).formatted(Formatting.RED));
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    PlayerVaultAdminUi.open((ServerPlayerEntity) player, economy);
                    return;
                }

                if (slot == navRowStart + 3 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }

                if (slot == navRowStart + 5) {
                    int unlockedTemp = economy.getPlayerVaults().getUnlockedVaultCount(target, EconomyConfig.get().playerVaultMaxAmount);
                    int maxVaults = Math.max(unlockedTemp, EconomyConfig.get().playerVaultDefaultAmount);
                    if ((page + 1) * 45 < maxVaults) {
                        page++;
                        updatePage();
                    }
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

        // Removed client-side rename helper (referenced undefined client classes/vars). Server-side renaming
        // is handled via the anvil handler in PlayerVaultUi.openRenameAnvil.
    }
}
