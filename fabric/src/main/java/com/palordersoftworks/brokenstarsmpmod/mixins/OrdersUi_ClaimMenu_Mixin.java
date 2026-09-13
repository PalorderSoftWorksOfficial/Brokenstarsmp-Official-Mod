package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ClickKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adds Drop All to EconomyCraft 1.9.0's claims menu and routes every collection
 * through the authoritative delivery ledger ({@code DeliveryManager}), never through
 * the displayed container contents.
 *
 * EconomyCraft's own click handling copies the *displayed* stack, inserts the copy
 * into the player inventory and only then removes the matching ledger entry. Because
 * {@code updatePage()} snapshots ledger references into the container, a refresh race
 * (or a stale page after another claim) lets a displayed stack be delivered while the
 * ledger removal no-ops - the Orders Drop All duplication exploit.
 *
 * Here the flow is reversed: the entry is removed from the live ledger list first
 * (identity-based, verified by a size delta), then delivered from the claimed copy.
 * {@code DeliveryManager.removeDelivery} persists the ledger immediately on success,
 * so a claimed entry can never be claimed again - not by a second click, a duplicate
 * packet, a GUI refresh, or a restart. A failed/partial inventory insert is rolled
 * back by re-adding only the exact remainder.
 *
 * All menu callbacks execute on the server thread; claims are therefore serialized
 * by the main loop and cannot interleave.
 */
@Mixin(targets = "com.reazip.economycraft.orders.OrdersUi$ClaimMenu", remap = false)
public abstract class OrdersUi_ClaimMenu_Mixin {
    @Unique
    private static final int bs$ITEMS_PER_PAGE = 45;
    @Unique
    private static final int bs$DROP_ALL_SLOT = 52; // footer: navRowStart(45) + 7, left of the page indicator

    @Shadow @Final private SimpleContainer container;
    @Shadow @Final private EconomyManager eco;
    @Shadow @Final private UUID owner;
    @Shadow private int page;

    @Shadow
    private void updatePage() {}

    @Inject(method = "updatePage", at = @At("RETURN"))
    private void bs$addDropAllButton(CallbackInfo ci) {
        ItemStack dropAll = new ItemStack(Items.DROPPER);
        dropAll.set(DataComponents.CUSTOM_NAME, Component.literal("Drop All")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GREEN)));
        dropAll.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Drops every delivery on this page.")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("Claimed server-side; each delivery is granted once.")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY))
        )));
        // Injected after MenuUiSupport.fillFooter, so the button survives footer refreshes.
        container.setItem(bs$DROP_ALL_SLOT, dropAll);
    }

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true)
    private void bs$handleClick(
            int slot,
            int dragType,
            ClickKind kind,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || kind != ClickKind.PICKUP) {
            return;
        }

        if (slot == bs$DROP_ALL_SLOT) {
            bs$dropAllOnPage(serverPlayer);
            cir.setReturnValue(true);
            return;
        }

        if (slot >= 0 && slot < bs$ITEMS_PER_PAGE) {
            ItemStack claimed = bs$claimAtPageSlot(slot);
            if (claimed == null) {
                // Stale display or empty slot: nothing is authoritative anymore.
                // Refresh so the GUI can never show (or re-deliver) a dead entry.
                updatePage();
                cir.setReturnValue(true);
                return;
            }

            bs$deliverOrRollback(serverPlayer, claimed);
            updatePage();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void bs$handleQuickMove(
            Player player,
            int slot,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || slot < 0 || slot >= bs$ITEMS_PER_PAGE) {
            return;
        }

        ItemStack claimed = bs$claimAtPageSlot(slot);
        if (claimed == null) {
            updatePage();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        bs$deliverOrRollback(serverPlayer, claimed);
        updatePage();
        cir.setReturnValue(claimed);
    }

    /**
     * Removes the ledger entry that backs the given page slot and returns a private
     * copy of it. The live ledger list is used as the single source of truth; removal
     * is verified via a size delta, so an already-claimed (stale) slot yields null
     * instead of a second copy.
     */
    @Unique
    private ItemStack bs$claimAtPageSlot(int slot) {
        List<ItemStack> live = eco.getDeliveries().getDeliveries(owner);
        int index = page * bs$ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= live.size()) {
            return null;
        }

        ItemStack entry = live.get(index);
        int before = live.size();
        eco.getDeliveries().removeDelivery(owner, entry);
        if (live.size() != before - 1) {
            // Removal did not happen (entry already claimed elsewhere): never deliver.
            return null;
        }
        return entry.copy();
    }

    /** Inserts a claimed stack; any remainder (inventory full) goes back to the ledger. */
    @Unique
    private void bs$deliverOrRollback(ServerPlayer player, ItemStack claimed) {
        if (!player.getInventory().add(claimed) && !claimed.isEmpty()) {
            // add() shrinks `claimed` to whatever did not fit; re-add exactly that.
            eco.getDeliveries().addDelivery(owner, claimed);
            player.sendSystemMessage(Component.literal("Your inventory is full - the items went back to your deliveries.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Claims every delivery on the current page, then drops the claimed stacks.
     * Claims all happen before any delivery, and each claim removes its entry from
     * the authoritative ledger, so repeated clicks can only ever observe an empty page.
     */
    @Unique
    private void bs$dropAllOnPage(ServerPlayer player) {
        List<ItemStack> claimed = new ArrayList<>();
        int start = page * bs$ITEMS_PER_PAGE;

        // Removals shift later entries down, so always claim at `start` to walk the page.
        for (int i = 0; i < bs$ITEMS_PER_PAGE; i++) {
            List<ItemStack> live = eco.getDeliveries().getDeliveries(owner);
            if (start >= live.size()) {
                break;
            }
            ItemStack entry = live.get(start);
            int before = live.size();
            eco.getDeliveries().removeDelivery(owner, entry);
            if (live.size() != before - 1) {
                break;
            }
            claimed.add(entry.copy());
        }

        if (claimed.isEmpty()) {
            updatePage();
            player.sendSystemMessage(Component.literal("Nothing to collect.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Claim (and persist) first, deliver second: a crash in between can lose a
        // drop but can never duplicate one.
        for (ItemStack stack : claimed) {
            player.drop(stack, false);
        }
        updatePage();
        player.sendSystemMessage(Component.literal("Dropped " + claimed.size() + " deliveries.")
                .withStyle(ChatFormatting.GREEN));
    }
}
