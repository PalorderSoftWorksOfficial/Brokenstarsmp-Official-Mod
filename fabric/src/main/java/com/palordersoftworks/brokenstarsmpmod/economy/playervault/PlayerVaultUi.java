package com.palordersoftworks.brokenstarsmpmod.economy.playervault;

import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtras;
import com.palordersoftworks.brokenstarsmpmod.economy.EconomyExtrasConfig;
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
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PlayerVaultUi {
    private PlayerVaultUi() {}

    public static void open(ServerPlayer player, int vaultIndex) {
        openFor(player, player.getUUID(), vaultIndex, null);
    }

    public static void openFor(ServerPlayer viewer, UUID owner, int vaultIndex, String ownerDisplay) {
        int rows = clampRows(EconomyExtrasConfig.get().playerVaultRows);
        PlayerVaultManager vaults = EconomyExtras.getVaults(viewer.level().getServer());
        SimpleContainer vault = vaults.prepareVault(owner, vaultIndex, rows);
        String name = vaults.getVaultName(owner, vaultIndex);
        String title;
        if (ownerDisplay != null) {
            title = name == null
                    ? "Vault #" + vaultIndex + " (" + ownerDisplay + ")"
                    : "Vault #" + vaultIndex + " - " + name + " (" + ownerDisplay + ")";
        } else {
            title = name == null ? "Vault #" + vaultIndex : "Vault #" + vaultIndex + " - " + name;
        }

        final String display = title;
        viewer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(display);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new SavingVaultHandler(id, inv, vault, rows, vaults);
            }
        });
    }

    static void openRenameAnvil(ServerPlayer player, UUID owner, int vaultIndex) {
        PlayerVaultManager vaults = EconomyExtras.getVaults(player.level().getServer());
        String currentName = vaults.getVaultName(owner, vaultIndex);
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Rename Vault #" + vaultIndex);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new VaultAnvilHandler(id, inv, vaults, owner, vaultIndex, currentName);
            }
        });
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private static MenuType<?> typeForRows(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static final class SavingVaultHandler extends ChestMenu {
        private final PlayerVaultManager manager;

        SavingVaultHandler(int id, Inventory playerInventory, SimpleContainer vault, int rows, PlayerVaultManager manager) {
            super(typeForRows(rows), id, playerInventory, vault, rows);
            this.manager = manager;
        }

        @Override
        public void removed(Player player) {
            manager.save();
            super.removed(player);
        }
    }

    private static final class VaultAnvilHandler extends AnvilMenu {
        private final PlayerVaultManager manager;
        private final UUID owner;
        private final int vaultIndex;

        VaultAnvilHandler(
                int id,
                Inventory playerInventory,
                PlayerVaultManager manager,
                UUID owner,
                int vaultIndex,
                String currentName
        ) {
            super(id, playerInventory, ContainerLevelAccess.NULL);
            this.manager = manager;
            this.owner = owner;
            this.vaultIndex = vaultIndex;

            ItemStack paper = new ItemStack(Items.PAPER);
            if (currentName != null && !currentName.isBlank()) {
                paper.set(DataComponents.CUSTOM_NAME, Component.literal(currentName));
            }
            this.inputSlots.setItem(0, paper);
            this.createResult();
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            String newName = stack.get(DataComponents.CUSTOM_NAME) != null
                    ? stack.getHoverName().getString()
                    : null;
            manager.setVaultName(owner, vaultIndex, newName);
            if (player instanceof ServerPlayer serverPlayer) {
                if (newName == null || newName.isBlank()) {
                    serverPlayer.sendSystemMessage(Component.literal("Cleared name for Vault #" + vaultIndex + ".")
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "Named Vault #" + vaultIndex + " to \"" + newName + "\".")
                            .withStyle(ChatFormatting.GREEN));
                }
                try {
                    this.inputSlots.setItem(0, ItemStack.EMPTY);
                    this.inputSlots.setItem(1, ItemStack.EMPTY);
                    this.createResult();
                } catch (Exception ignored) {
                }
                manager.save();
                serverPlayer.closeContainer();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
