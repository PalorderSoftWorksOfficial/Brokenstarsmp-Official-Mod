package com.palordersoftworks.brokenstarsmpmod.economy.playervault;

import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtrasConfig;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
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

public final class PlayerVaultPickerUi {
    private PlayerVaultPickerUi() {}

    public static void open(ServerPlayer player) {
        int max = PlayerVaultCommands.resolveMaxVaults(player);
        if (max <= 0) {
            player.sendSystemMessage(Component.literal("Player vaults are disabled for you.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Player Vaults");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PickerMenu(id, inv, player, max);
            }
        });
    }

    private static final class PickerMenu extends AbstractContainerMenu {
        private final ServerPlayer viewer;
        private final PlayerVaultManager vaults;
        private final EconomyManager eco;
        private final int maxVaults;
        private int unlocked;
        private final SimpleContainer container = new SimpleContainer(54);

        PickerMenu(int id, Inventory inv, ServerPlayer viewer, int maxVaults) {
            super(MenuType.GENERIC_9x6, id);
            this.viewer = viewer;
            this.vaults = EconomyExtras.getVaults(viewer.level().getServer());
            this.eco = EconomyCraft.getManager(viewer.level().getServer());
            this.maxVaults = maxVaults;
            this.unlocked = vaults.getUnlockedVaultCount(viewer.getUUID(), maxVaults);
            render();
            for (int i = 0; i < 54; i++) {
                int idx = i;
                this.addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
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

        private void render() {
            unlocked = vaults.getUnlockedVaultCount(viewer.getUUID(), maxVaults);
            container.clearContent();
            for (int i = 1; i <= unlocked && i <= 45; i++) {
                String vName = vaults.getVaultName(viewer.getUUID(), i);
                ItemStack chest = new ItemStack(Items.CHEST);
                chest.set(DataComponents.CUSTOM_NAME, Component.literal(
                        vName == null ? "Vault #" + i : "Vault #" + i + " - " + vName)
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("Left-click to open")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
                lore.add(Component.literal("Right-click to rename")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));
                chest.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i - 1, chest);
            }

            if (unlocked < maxVaults) {
                long cost = EconomyExtrasConfig.get().playerVaultUnlockCost;
                ItemStack create = new ItemStack(Items.EMERALD);
                create.set(DataComponents.CUSTOM_NAME, Component.literal("Unlock Vault #" + (unlocked + 1))
                        .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal(cost <= 0 ? "Free" : "Cost: " + EconomyCraft.formatMoney(cost))
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
                create.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(45, create);
            }

            long balance = eco.getBalance(viewer.getUUID(), true);
            ItemStack bal = new ItemStack(Items.GOLD_INGOT);
            bal.set(DataComponents.CUSTOM_NAME, Component.literal("Balance: " + EconomyCraft.formatMoney(balance))
                    .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
            container.setItem(49, bal);

            if (PermissionCompat.gamemaster().test(viewer.createCommandSourceStack())) {
                ItemStack admin = new ItemStack(Items.COMMAND_BLOCK);
                admin.set(DataComponents.CUSTOM_NAME, Component.literal("Admin Vaults")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
                container.setItem(53, admin);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clicked(int slot, int button, ContainerInput type, Player player) {
            if (type == ContainerInput.PICKUP && slot >= 0 && slot < 54) {
                if (slot < unlocked) {
                    int vaultIndex = slot + 1;
                    if (button == 1) {
                        PlayerVaultUi.openRenameAnvil(viewer, viewer.getUUID(), vaultIndex);
                    } else {
                        PlayerVaultUi.open(viewer, vaultIndex);
                    }
                    return;
                }
                if (slot == 45 && unlocked < maxVaults) {
                    long cost = EconomyExtrasConfig.get().playerVaultUnlockCost;
                    if (cost > 0 && !eco.removeMoney(viewer.getUUID(), cost)) {
                        viewer.sendSystemMessage(Component.literal("Not enough balance.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    vaults.tryUnlockNextVault(viewer.getUUID(), maxVaults);
                    render();
                    return;
                }
                if (slot == 53 && PermissionCompat.gamemaster().test(viewer.createCommandSourceStack())) {
                    PlayerVaultAdminUi.open(viewer);
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
