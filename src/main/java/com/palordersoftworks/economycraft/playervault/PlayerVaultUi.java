package com.palordersoftworks.economycraft.playervault;

import com.palordersoftworks.economycraft.EconomyConfig;
import com.palordersoftworks.economycraft.EconomyManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Formatting;

public final class PlayerVaultUi {
    private PlayerVaultUi() {}

    public static void open(ServerPlayerEntity player, EconomyManager economy, int vaultIndex) {
        int rows = clampRows(EconomyConfig.get().playerVaultRows);
        SimpleInventory vault = economy.getPlayerVaults().prepareVault(player.getUuid(), vaultIndex, rows);
        String name = economy.getPlayerVaults().getVaultName(player.getUuid(), vaultIndex);
        Text title = name == null
                ? Text.literal("Vault #" + vaultIndex)
                : Text.literal("Vault #" + vaultIndex + " - " + name);

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

    public static void openFor(ServerPlayerEntity viewer, EconomyManager economy, java.util.UUID owner, int vaultIndex) {
        int rows = clampRows(EconomyConfig.get().playerVaultRows);
        SimpleInventory vault = economy.getPlayerVaults().prepareVault(owner, vaultIndex, rows);
        String name = economy.getPlayerVaults().getVaultName(owner, vaultIndex);
        Text title = name == null
                ? Text.literal("Vault #" + vaultIndex + " (" + economy.getBestName(owner) + ")")
                : Text.literal("Vault #" + vaultIndex + " - " + name + " (" + economy.getBestName(owner) + ")");

        viewer.openHandledScreen(new NamedScreenHandlerFactory() {
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

    static void openRenameAnvil(ServerPlayerEntity player, EconomyManager economy, java.util.UUID owner, int vaultIndex) {
        String currentName = economy.getPlayerVaults().getVaultName(owner, vaultIndex);
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Rename Vault #" + vaultIndex);
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new VaultAnvilHandler(syncId, inv, economy.getPlayerVaults(), owner, vaultIndex, currentName);
            }
        });
    }

    private static final class VaultAnvilHandler extends AnvilScreenHandler {
        private final PlayerVaultManager manager;
        private final java.util.UUID owner;
        private final int vaultIndex;

        VaultAnvilHandler(int syncId, PlayerInventory playerInventory, PlayerVaultManager manager, java.util.UUID owner, int vaultIndex, String currentName) {
            super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
            this.manager = manager;
            this.owner = owner;
            this.vaultIndex = vaultIndex;

            ItemStack paper = new ItemStack(Items.PAPER);
            if (currentName != null && !currentName.isBlank()) {
                paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal(currentName));
            }
            this.input.setStack(0, paper);
            this.updateResult();
        }

        @Override
        protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
            super.onTakeOutput(player, stack);
            String newName = stack.getCustomName() != null ? stack.getCustomName().getString() : null;
            manager.setVaultName(owner, vaultIndex, newName);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (newName == null || newName.isBlank()) {
                    serverPlayer.sendMessage(Text.literal("Cleared name for Vault #" + vaultIndex + ".").formatted(Formatting.GREEN), false);
                } else {
                    serverPlayer.sendMessage(Text.literal("Named Vault #" + vaultIndex + " to \"" + newName + "\".").formatted(Formatting.GREEN), false);
                }
            }
            // prevent duplication: ensure any input/result stacks are cleared after taking output
            try {
                // clear input slots (both) and call updateResult to recompute output (will be empty)
                this.input.setStack(0, ItemStack.EMPTY);
                this.input.setStack(1, ItemStack.EMPTY);
                this.updateResult();
            } catch (Exception ignored) {       
            }
            manager.save();
            // close the anvil UI for the player to avoid any remaining client-side interaction
            if (player instanceof ServerPlayerEntity serverPlayer) {
                try {
                    serverPlayer.closeHandledScreen();
                } catch (Exception ignored) { }
            }
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }
    }

    // Client-only helper removed; renaming is performed server-side via the Anvil handler above.
}
