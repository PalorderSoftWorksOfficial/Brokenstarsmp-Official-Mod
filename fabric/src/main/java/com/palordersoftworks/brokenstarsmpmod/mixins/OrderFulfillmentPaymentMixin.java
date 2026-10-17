package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.reazip.economycraft.api.v1.MutationSource;
import com.reazip.economycraft.api.v1.PaymentResult;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.orders.OrderFulfillment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * EconomyCraft 1.10.0 has a bug in {@link OrderFulfillment#settleOrderPayment}
 * where the {@code transferMoney} call passes the fulfiller as the sender and the
 * requester as the receiver — the opposite of what a fulfillment payment should do.
 * This mixin redirects that call so money flows from the requester to the fulfiller.
 *
 * The {@code remaining > 0} branch (when the escrow does not cover the full payment)
 * is the affected path; the {@code remaining <= 0} branch already uses {@code addMoney}
 * on the fulfiller and is correct.
 */
@Mixin(OrderFulfillment.class)
public abstract class OrderFulfillmentPaymentMixin {

    /**
     * Redirects the transferMoney call inside settleOrderPayment so the direction is
     * requester -&gt; fulfiller instead of fulfiller -&gt; requester.
     *
     * The original call signature (derived from bytecode) is:
     *   transferMoney(fulfillerUUID, requesterUUID, remaining, source, desc)
     *
     * We swap the first two arguments so it becomes:
     *   transferMoney(requesterUUID, fulfillerUUID, remaining, source, desc)
     */
    @Redirect(
            method = "settleOrderPayment",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/reazip/economycraft/EconomyManager;transferMoney(Ljava/util/UUID;Ljava/util/UUID;JJLcom/reazip/economycraft/api/v1/MutationSource;Ljava/lang/String;)Lcom/reazip/economycraft/api/v1/PaymentResult;"
            )
    )
    private static PaymentResult bs$fixFulfillmentPaymentDirection(
            EconomyManager manager,
            UUID fromUuid,
            UUID toUuid,
            long amount,
            long secondaryAmount,
            MutationSource source,
            String description) {
        // In settleOrderPayment the arguments are (fulfiller, requester, remaining, escrowUsed, source, desc).
        // Swap from/to so the requester pays the fulfiller.
        return manager.transferMoney(toUuid, fromUuid, amount, secondaryAmount, source, description);
    }
}
