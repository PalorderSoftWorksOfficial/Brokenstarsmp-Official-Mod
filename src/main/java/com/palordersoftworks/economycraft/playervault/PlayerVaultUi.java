package com.palordersoftworks.economycraft.playervault;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class PlayerVaultUi {
    private PlayerVaultUi() {}

    public static void open(ServerPlayerEntity player, EconomyManager economy, int vaultIndex) {
        int rows = clampRows(EconomyConfig.get().playerVaultRows);
        SimpleInventory vault = economy.getPlayerVaults().prepareVault(player.getUuid(), vaultIndex, rows);
        Text title = Text.literal("Vault #" + vaultIndex);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new SavingVaultHandler(syncId, inv, vault, rows, economy.getPlayerVaults());
            }
        });
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private static ScreenHandlerType<?> typeForRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    private static final class SavingVaultHandler extends GenericContainerScreenHandler {
        private final PlayerVaultManager manager;

        SavingVaultHandler(
                int syncId,
                PlayerInventory playerInventory,
                SimpleInventory vault,
                int rows,
                PlayerVaultManager manager
        ) {
            super(typeForRows(rows), syncId, playerInventory, vault, rows);
            this.manager = manager;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            manager.save();
            super.onClosed(player);
        }
    }
}
