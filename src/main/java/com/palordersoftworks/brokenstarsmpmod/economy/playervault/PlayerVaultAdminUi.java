package com.palordersoftworks.brokenstarsmpmod.economy.playervault;

import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtrasConfig;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.reazip.economycraft.EconomyCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class PlayerVaultAdminUi {
    private PlayerVaultAdminUi() {}

    public static void open(ServerPlayer admin) {
        if (!PermissionCompat.gamemaster().test(admin.createCommandSourceStack())) {
            admin.sendSystemMessage(Component.literal("No permission.").withStyle(ChatFormatting.RED));
            return;
        }
        admin.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Vault Admin");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PlayerListMenu(id, inv, admin);
            }
        });
    }

    public static void openTarget(ServerPlayer admin, UUID target) {
        String name = EconomyCraft.getManager(admin.level().getServer()).getBestName(target);
        admin.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Vaults: " + (name == null ? target.toString() : name));
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new TargetMenu(id, inv, admin, target, name == null ? target.toString() : name);
            }
        });
    }

    private static final class PlayerListMenu extends AbstractContainerMenu {
        private final ServerPlayer admin;
        private final PlayerVaultManager vaults;
        private final List<UUID> players;
        private final SimpleContainer container = new SimpleContainer(54);

        PlayerListMenu(int id, Inventory inv, ServerPlayer admin) {
            super(MenuType.GENERIC_9x6, id);
            this.admin = admin;
            this.vaults = EconomyExtras.getVaults(admin.level().getServer());
            this.players = new ArrayList<>(vaults.getTrackedPlayers());
            render();
            for (int i = 0; i < 54; i++) {
                this.addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
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

        private void render() {
            container.clearContent();
            int max = EconomyExtrasConfig.get().playerVaultMaxAmount;
            for (int i = 0; i < Math.min(45, players.size()); i++) {
                UUID uuid = players.get(i);
                String name = EconomyCraft.getManager(admin.level().getServer()).getBestName(uuid);
                int unlocked = vaults.getUnlockedVaultCount(uuid, max);
                ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                head.set(DataComponents.CUSTOM_NAME, Component.literal(name == null ? uuid.toString() : name)
                        .withStyle(s -> s.withItalic(false)));
                head.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Unlocked: " + unlocked)
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))
                )));
                container.setItem(i, head);
            }
            ItemStack back = new ItemStack(Items.ARROW);
            back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to my vaults")
                    .withStyle(s -> s.withItalic(false)));
            container.setItem(49, back);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clicked(int slot, int button, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP) {
                if (slot >= 0 && slot < players.size() && slot < 45) {
                    openTarget(admin, players.get(slot));
                    return;
                }
                if (slot == 49) {
                    PlayerVaultPickerUi.open(admin);
                    return;
                }
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    private static final class TargetMenu extends AbstractContainerMenu {
        private final ServerPlayer admin;
        private final UUID target;
        private final String targetName;
        private final PlayerVaultManager vaults;
        private final SimpleContainer container = new SimpleContainer(54);

        TargetMenu(int id, Inventory inv, ServerPlayer admin, UUID target, String targetName) {
            super(MenuType.GENERIC_9x6, id);
            this.admin = admin;
            this.target = target;
            this.targetName = targetName;
            this.vaults = EconomyExtras.getVaults(admin.level().getServer());
            render();
            for (int i = 0; i < 54; i++) {
                this.addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
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

        private void render() {
            container.clearContent();
            int max = EconomyExtrasConfig.get().playerVaultMaxAmount;
            int unlocked = vaults.getUnlockedVaultCount(target, max);
            for (int i = 1; i <= unlocked && i <= 45; i++) {
                String vName = vaults.getVaultName(target, i);
                ItemStack chest = new ItemStack(Items.CHEST);
                chest.set(DataComponents.CUSTOM_NAME, Component.literal(
                        vName == null ? "Vault #" + i : "Vault #" + i + " - " + vName)
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
                chest.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Click to open").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                        Component.literal("Shift-click to clear").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED))
                )));
                container.setItem(i - 1, chest);
            }
            ItemStack back = new ItemStack(Items.ARROW);
            back.set(DataComponents.CUSTOM_NAME, Component.literal("Back")
                    .withStyle(s -> s.withItalic(false)));
            container.setItem(49, back);
            ItemStack wipe = new ItemStack(Items.BARRIER);
            wipe.set(DataComponents.CUSTOM_NAME, Component.literal("Clear all vaults")
                    .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED)));
            container.setItem(53, wipe);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clicked(int slot, int button, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE) {
                int unlocked = vaults.getUnlockedVaultCount(target, EconomyExtrasConfig.get().playerVaultMaxAmount);
                if (slot >= 0 && slot < unlocked) {
                    int vaultIndex = slot + 1;
                    if (type == ContainerInput.QUICK_MOVE) {
                        vaults.clearVault(target, vaultIndex);
                        render();
                    } else {
                        PlayerVaultUi.openFor(admin, target, vaultIndex, targetName);
                    }
                    return;
                }
                if (slot == 49) {
                    open(admin);
                    return;
                }
                if (slot == 53) {
                    vaults.clearAllVaults(target);
                    render();
                    return;
                }
                return;
            }
            super.clicked(slot, button, type, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
