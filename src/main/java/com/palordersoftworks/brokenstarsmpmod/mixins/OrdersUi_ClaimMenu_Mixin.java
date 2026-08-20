package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.reazip.economycraft.util.ClickKind;
import java.util.List;
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

/**
 * Restores BrokenStars Drop All on EconomyCraft's deliveries GUI
 * (upstream uses that slot for Main menu only).
 */
@Mixin(targets = "com.reazip.economycraft.orders.OrdersUi$ClaimMenu", remap = false)
public abstract class OrdersUi_ClaimMenu_Mixin {

    @Shadow @Final private SimpleContainer container;
    @Shadow @Final private List items;
    @Shadow private int page;
    @Shadow @Final private int navRowStart;

    @Shadow
    private void updatePage() {}

    @Shadow
    private void removeStack(ItemStack stack) {}

    @Unique
    private static final int DROP_ALL_SLOT_OFFSET = 7;

    @Inject(method = "updatePage", at = @At("RETURN"))
    private void brokenstarsmp$addDropAllButton(CallbackInfo ci) {
        ItemStack dropAll = new ItemStack(Items.DROPPER);
        dropAll.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Drop All")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GREEN))
        );
        dropAll.set(
                DataComponents.LORE,
                new ItemLore(List.of(
                        Component.literal("Drops every item")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                        Component.literal("on this page.")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))
                ))
        );
        container.setItem(navRowStart + DROP_ALL_SLOT_OFFSET, dropAll);
    }

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true)
    private void brokenstarsmp$handleDropAll(
            int slot,
            int dragType,
            ClickKind kind,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (kind != ClickKind.PICKUP) {
            return;
        }
        if (slot != navRowStart + DROP_ALL_SLOT_OFFSET) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            cir.setReturnValue(true);
            return;
        }
        brokenstarsmp$dropAllOnPage(serverPlayer);
        cir.setReturnValue(true);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void brokenstarsmp$dropAllOnPage(ServerPlayer player) {
        int start = page * 45;
        int end = Math.min(start + 45, items.size());
        int dropped = 0;

        // Back-to-front so removeStack index shifts do not skip entries.
        for (int i = end - 1; i >= start; i--) {
            Object raw = items.get(i);
            if (!(raw instanceof ItemStack stack) || stack.isEmpty()) {
                continue;
            }
            player.drop(stack.copy(), false);
            removeStack(stack);
            dropped++;
        }

        int remaining = Math.max(0, items.size() - dropped);
        int totalPages = Math.max(1, (int) Math.ceil(remaining / 45.0));
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        updatePage();

        if (dropped > 0) {
            player.sendSystemMessage(
                    Component.literal("Dropped " + dropped + " deliveries.")
                            .withStyle(ChatFormatting.GREEN)
            );
        } else {
            player.sendSystemMessage(
                    Component.literal("No deliveries to drop.")
                            .withStyle(ChatFormatting.RED)
            );
        }
    }
}
