package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.IdentifierCompat;
import com.reazip.economycraft.util.PermissionCompat;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import java.util.concurrent.CompletableFuture;
import com.reazip.economycraft.shop.ShopManager;
import com.reazip.economycraft.shop.ShopListing;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.shop.ServerShopUi;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.orders.OrderRequest;
import com.reazip.economycraft.orders.OrdersUi;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EconomyCommands {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(buildRoot(
                buildAddMoney(),
                buildSetMoney(),
                buildRemoveMoney(),
                buildRemovePlayer(),
                buildToggleScoreboard()
        ));

        dispatcher.register(buildBalance().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildPay().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(SellCommand.register().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildShop().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildOrders().requires(s -> EconomyConfig.get().standaloneCommands));
        dispatcher.register(buildDaily().requires(s -> EconomyConfig.get().standaloneCommands));

        dispatcher.register(
                buildAddMoney().requires(src ->
                        PermissionCompat.gamemaster().test(src)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildSetMoney().requires(src ->
                        PermissionCompat.gamemaster().test(src)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildRemoveMoney().requires(src ->
                        PermissionCompat.gamemaster().test(src)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildRemovePlayer().requires(src ->
                        PermissionCompat.gamemaster().test(src)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        dispatcher.register(
                buildToggleScoreboard().requires(src ->
                        PermissionCompat.gamemaster().test(src)
                                && EconomyConfig.get().standaloneAdminCommands
                )
        );

        var serverShop = buildServerShop();
        serverShop.requires(
                serverShop.getRequirement()
                        .and(src -> EconomyConfig.get().standaloneCommands)
        );
        dispatcher.register(serverShop);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRoot(
            LiteralArgumentBuilder<ServerCommandSource> addMoney,
            LiteralArgumentBuilder<ServerCommandSource> setMoney,
            LiteralArgumentBuilder<ServerCommandSource> removeMoney,
            LiteralArgumentBuilder<ServerCommandSource> removePlayer,
            LiteralArgumentBuilder<ServerCommandSource> toggleScoreboard
    ) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("eco");

        root.then(buildBalance());
        root.then(buildPay());
        root.then(SellCommand.register());
        root.then(buildShop());
        root.then(buildOrders());
        root.then(buildDaily());

        root.then(addMoney);
        root.then(setMoney);
        root.then(removeMoney);
        root.then(removePlayer);
        root.then(toggleScoreboard);

        if (EconomyConfig.get().serverShopEnabled) {
            root.then(buildServerShop());
        }

        return root;
    }

    // =====================================================================
    // === Balance & payments ==============================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildBalance() {
        return literal("bal")
                .then(literal("top")
                        .executes(ctx -> balTop(ctx.getSource())))
                .executes(ctx -> showBalance(IdentityCompat.of(ctx.getSource().getPlayerOrThrow()), ctx.getSource()))
                .then(argument("target", GameProfileArgumentType.gameProfile())
                        .executes(ctx -> {
                            var refs = IdentityCompat.getArgAsPlayerRefs(ctx, "target");
                            if (refs.size() != 1) {
                                ctx.getSource().sendError(Text.literal("Please specify exactly one player").formatted(Formatting.RED));
                                return 0;
                            }
                            return showBalance(refs.iterator().next(), ctx.getSource());
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPay() {
        return literal("pay")
                .then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestPlayers(ctx.getSource(), builder))
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> pay(ctx.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount"), ctx.getSource()))));
    }

    private static int showBalance(IdentityCompat.PlayerRef target, ServerCommandSource source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Long bal = manager.getBalance(target.id(), false);
        if (bal == null) {
            source.sendError(Text.literal("Unknown player").formatted(Formatting.RED));
            return 0;
        }

        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        Text msg;
        if (executor != null && executor.getUuid().equals(target.id())) {
            msg = Text.literal("Balance: " + EconomyCraft.formatMoney(bal))
                    .formatted(Formatting.YELLOW);
        } else {
            msg = Text.literal(target.name() + "'s balance: " + EconomyCraft.formatMoney(bal))
                    .formatted(Formatting.YELLOW);
        }

        if (executor != null) {
            executor.sendMessage(msg);
        } else {
            source.sendFeedback(() -> msg, false);
        }

        return 1;
    }

    private static int balTop(ServerCommandSource source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        Map<UUID, Long> balances = manager.getBalances();

        if (balances.isEmpty()) {
            source.sendError(Text.literal("No balances found").formatted(Formatting.RED));
            return 0;
        }

        var sorted = getSortedEntries(balances, manager);
        if (sorted.size() > 10) sorted = new ArrayList<>(sorted.subList(0, 10));

        StringBuilder sb = new StringBuilder("Top balances:\n");
        for (int i = 0; i < sorted.size(); i++) {
            var e = sorted.get(i);
            UUID id = e.getKey();
            long balance = e.getValue();

            String name = manager.getBestName(id);
            if (name == null || name.isBlank()) name = id.toString();

            sb.append(i + 1)
                    .append(". ")
                    .append(name)
                    .append(": ")
                    .append(EconomyCraft.formatMoney(balance));

            if (i + 1 < sorted.size()) sb.append("\n");
        }

        Text msg = Text.literal(sb.toString()).formatted(Formatting.GOLD);

        ServerPlayerEntity executor;
        try { executor = source.getPlayerOrThrow(); }
        catch (Exception ex) { executor = null; }

        if (executor != null) executor.sendMessage(msg);
        else source.sendFeedback(() -> msg, false);

        return sorted.size();
    }

    private static @NotNull ArrayList<Map.Entry<UUID, Long>> getSortedEntries(Map<UUID, Long> balances, EconomyManager manager) {
        var sorted = new ArrayList<>(balances.entrySet());
        sorted.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            if (c != 0) return c;

            String an = manager.getBestName(a.getKey());
            String bn = manager.getBestName(b.getKey());
            if (an == null || an.isBlank()) an = a.getKey().toString();
            if (bn == null || bn.isBlank()) bn = b.getKey().toString();

            c = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
            if (c != 0) return c;

            return a.getKey().compareTo(b.getKey());
        });
        return sorted;
    }

    private static int pay(ServerPlayerEntity from, String target, long amount, ServerCommandSource source) {
        var server = source.getServer();
        EconomyManager manager = EconomyCraft.getManager(server);

        ServerPlayerEntity toOnline = server.getPlayerManager().getPlayer(target);
        UUID toId = (toOnline != null) ? toOnline.getUuid() : null;

        if (toId == null) {
            try { toId = UUID.fromString(target); } catch (IllegalArgumentException ignored) {}
        }

        if (toId == null) {
            toId = manager.tryResolveUuidByName(target);
        }

        if (toId == null) {
            source.sendError(Text.literal("Unknown player").formatted(Formatting.RED));
            return 0;
        }

        if (from.getUuid().equals(toId)) {
            source.sendError(Text.literal("You cannot pay yourself").formatted(Formatting.RED));
            return 0;
        }

        if (!manager.getBalances().containsKey(toId)) {
            source.sendError(Text.literal("Unknown player").formatted(Formatting.RED));
            return 0;
        }

        if (manager.pay(from.getUuid(), toId, amount)) {
            String displayName = (toOnline != null)
                    ? IdentityCompat.of(toOnline).name()
                    : getDisplayName(manager, toId);

            ServerPlayerEntity executor;
            try {
                executor = source.getPlayerOrThrow();
            } catch (Exception e) {
                executor = null;
            }

            Text msg = Text.literal("Paid " + EconomyCraft.formatMoney(amount) + " to " + displayName)
                    .formatted(Formatting.GREEN);

            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, false);
            }

            if (toOnline != null) {
                toOnline.sendMessage(
                        Text.literal(from.getName().getString() + " sent you " + EconomyCraft.formatMoney(amount))
                                .formatted(Formatting.GREEN)
                );
            }
        } else {
            source.sendError(Text.literal("Not enough balance").formatted(Formatting.RED));
        }
        return 1;
    }

    // =====================================================================
    // === Admin commands ==================================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildAddMoney() {
        return literal("addmoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgumentType.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> addMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildSetMoney() {
        return literal("setmoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgumentType.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(0, EconomyManager.MAX))
                                .executes(ctx -> setMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRemoveMoney() {
        return literal("removemoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgumentType.gameProfile())
                        .executes(ctx -> removeMoney(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                null,
                                ctx.getSource()))
                        .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> removeMoney(
                                        IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                        LongArgumentType.getLong(ctx, "amount"),
                                        ctx.getSource()))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRemovePlayer() {
        return literal("removeplayer").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgumentType.gameProfile())
                        .executes(ctx -> removePlayers(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                ctx.getSource())));
    }

    private static int addMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, ServerCommandSource source) {
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("No targets matched").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.addMoney(p.id(), amount);

            Text msg = Text.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.name() + "'s balance.")
                    .formatted(Formatting.GREEN);

            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.addMoney(p.id(), amount);
        }

        int count = profiles.size();

        Text msg = Text.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .formatted(Formatting.GREEN);

        if (executor != null) {
            executor.sendMessage(msg);
        } else {
            source.sendFeedback(() -> msg, true);
        }

        return count;
    }

    private static int setMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, ServerCommandSource source) {
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("No targets matched").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());

        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.setMoney(p.id(), amount);

            Text msg = Text.literal(
                            "Set balance of " + p.name() + " to " + EconomyCraft.formatMoney(amount))
                    .formatted(Formatting.GREEN);

            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.setMoney(p.id(), amount);
        }

        int count = profiles.size();

        Text msg = Text.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .formatted(Formatting.GREEN);

        if (executor != null) {
            executor.sendMessage(msg);
        } else {
            source.sendFeedback(() -> msg, true);
        }

        return count;
    }

    private static int removeMoney(Collection<IdentityCompat.PlayerRef> profiles, Long amount, ServerCommandSource source) {
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("No targets matched").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        int success = 0;

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            UUID id = p.id();

            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendError(Text.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
                            .formatted(Formatting.RED));
                    return 1;
                }
                manager.setMoney(id, 0L);
                Text msg = Text.literal(
                                "Removed all money from " + p.name() + "'s balance.")
                        .formatted(Formatting.GREEN);
                if (executor != null) {
                    executor.sendMessage(msg);
                } else {
                    source.sendFeedback(() -> msg, true);
                }
                return 1;
            }

            if (!manager.removeMoney(id, amount)) {
                source.sendError(Text.literal(
                                "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                        .formatted(Formatting.RED));
                return 1;
            }

            Text msg = Text.literal(
                            "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance.")
                    .formatted(Formatting.GREEN);
            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, true);
            }
            return 1;
        }

        for (var p : profiles) {
            UUID id = p.id();
            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendError(Text.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
                            .formatted(Formatting.RED));
                    continue;
                }
                manager.setMoney(id, 0L);
                success++;
            } else {
                if (manager.removeMoney(id, amount)) {
                    success++;
                } else {
                    source.sendError(Text.literal(
                                    "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                            .formatted(Formatting.RED));
                }
            }
        }

        if (success > 0) {
            int finalSuccess = success;
            Text msg;
            if (amount == null) {
                msg = Text.literal(
                                "Removed all money from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .formatted(Formatting.GREEN);
            } else {
                msg = Text.literal(
                                "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + finalSuccess + " player" + (finalSuccess > 1 ? "s" : "") + ".")
                        .formatted(Formatting.GREEN);
            }
            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, true);
            }
        }

        return profiles.size();
    }

    private static int removePlayers(Collection<IdentityCompat.PlayerRef> profiles, ServerCommandSource source) {
        if (profiles.isEmpty()) {
            source.sendError(Text.literal("No targets matched").formatted(Formatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.removePlayer(p.id());

            Text msg = Text.literal("Removed " + p.name() + " from economy")
                    .formatted(Formatting.GREEN);

            if (executor != null) {
                executor.sendMessage(msg);
            } else {
                source.sendFeedback(() -> msg, true);
            }

            return 1;
        }

        for (var p : profiles) {
            manager.removePlayer(p.id());
        }

        int count = profiles.size();

        Text msg = Text.literal(
                        "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .formatted(Formatting.GREEN);

        if (executor != null) {
            executor.sendMessage(msg);
        } else {
            source.sendFeedback(() -> msg, true);
        }

        return count;
    }

    // =====================================================================
    // === Scoreboard toggle ===============================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildToggleScoreboard() {
        return literal("toggleScoreboard").requires(PermissionCompat.gamemaster())
                .executes(ctx -> toggleScoreboard(ctx.getSource()));
    }

    private static int toggleScoreboard(ServerCommandSource source) {
        boolean enabled = EconomyCraft.getManager(source.getServer()).toggleScoreboard();

        ServerPlayerEntity executor;
        try {
            executor = source.getPlayerOrThrow();
        } catch (Exception e) {
            executor = null;
        }

        Text msg = Text.literal("Scoreboard " + (enabled ? "enabled" : "disabled"))
                .formatted(enabled ? Formatting.GREEN : Formatting.RED);

        if (executor != null) {
            executor.sendMessage(msg);
        } else {
            source.sendFeedback(() -> msg, false);
        }

        return 1;
    }

    // =====================================================================
    // === Shop commands ===================================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildShop() {
        return literal("shop")
                .executes(ctx -> openShop(ctx.getSource().getPlayerOrThrow(), ctx.getSource()))
                .then(literal("list")
                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                .executes(ctx -> listItem(ctx.getSource().getPlayerOrThrow(),
                                        LongArgumentType.getLong(ctx, "price"),
                                        ctx.getSource()))));
    }

    private static int openShop(ServerPlayerEntity player, ServerCommandSource source) {
        try {
            ShopUi.open(player, EconomyCraft.getManager(source.getServer()).getShop());
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /shop for {}", player.getDisplayName().getString(), e);
            source.sendError(Text.literal("Failed to open shop. Check server logs."));
            return 0;
        }
    }

    private static int listItem(ServerPlayerEntity player, long price, ServerCommandSource source) {
        if (player.getMainHandStack().isEmpty()) {
            source.sendError(Text.literal("Hold the item to list in your hand").formatted(Formatting.RED));
            return 0;
        }

        ShopManager shop = EconomyCraft.getManager(source.getServer()).getShop();
        ShopListing listing = new ShopListing();
        listing.seller = player.getUuid();
        listing.price = price;

        ItemStack hand = player.getMainHandStack();
        int count = Math.min(hand.getCount(), hand.getMaxCount());
        listing.item = hand.copyWithCount(count);
        hand.decrement(count);
        shop.addListing(listing);

        long tax = Math.round(price * EconomyConfig.get().taxRate);

        Text msg = Text.literal("Listed item for " + EconomyCraft.formatMoney(price) +
                        (tax > 0 ? " (buyers pay " + EconomyCraft.formatMoney(price + tax) + ")" : ""))
                .formatted(Formatting.GREEN);

        player.sendMessage(msg);

        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildServerShop() {
        return literal("servershop")
                .requires(src -> EconomyConfig.get().serverShopEnabled)
                .executes(ctx -> openServerShop(ctx.getSource().getPlayerOrThrow(), ctx.getSource(), null))
                .then(argument("category", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggestServerShopCategories(ctx.getSource(), builder))
                        .executes(ctx -> openServerShop(
                                ctx.getSource().getPlayerOrThrow(),
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "category")
                        )));
    }

    private static int openServerShop(ServerPlayerEntity player, ServerCommandSource source, @Nullable String category) {
        if (!EconomyConfig.get().serverShopEnabled) {
            source.sendError(Text.literal("Server shop is disabled.").formatted(Formatting.RED));
            return 0;
        }
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        try {
            ServerShopUi.open(player, manager, category);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /servershop for {} (category={})",
                    player.getDisplayName().getString(), category, e);
            source.sendError(Text.literal("Failed to open server shop. Check server logs."));
            return 0;
        }
    }

    // =====================================================================
    // === Orders commands =================================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildOrders() {
        return literal("orders")
                .executes(ctx -> openOrders(ctx.getSource().getPlayerOrThrow(), ctx.getSource()))
                .then(literal("request")
                        .then(argument("item", StringArgumentType.word())
                                .then(argument("amount", LongArgumentType.longArg(1, EconomyManager.MAX))
                                        .then(argument("price", LongArgumentType.longArg(1, EconomyManager.MAX))
                                                .executes(ctx -> requestItem(ctx.getSource().getPlayerOrThrow(),
                                                        StringArgumentType.getString(ctx, "item"),
                                                        (int) Math.min(LongArgumentType.getLong(ctx, "amount"), EconomyManager.MAX),
                                                        LongArgumentType.getLong(ctx, "price"),
                                                        ctx.getSource()))))))
                .then(literal("claim").executes(ctx -> claimOrders(ctx.getSource().getPlayerOrThrow(), ctx.getSource())));
    }

    private static int openOrders(ServerPlayerEntity player, ServerCommandSource source) {
        try {
            OrdersUi.open(player, EconomyCraft.getManager(source.getServer()));
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open /orders for {}", player.getDisplayName().getString(), e);
            source.sendError(Text.literal("Failed to open orders. Check server logs."));
            return 0;
        }
    }

    private static int requestItem(ServerPlayerEntity player, String itemId, int amount, long price, ServerCommandSource source) {
        IdentifierCompat.Id item = IdentifierCompat.tryParse(itemId);
        var holder = IdentifierCompat.registryGetOptional(Registries.ITEM, item);
        if (holder.isEmpty()) {
            source.sendError(Text.literal("Invalid item").formatted(Formatting.RED));
            return 0;
        }
        OrderManager orders = EconomyCraft.getManager(source.getServer()).getOrders();
        OrderRequest r = new OrderRequest();
        r.requester = player.getUuid();
        r.price = price;
        r.item = new ItemStack(holder.get());
        int maxAmount = 36 * r.item.getMaxCount();
        if (amount > maxAmount) {
            source.sendError(Text.literal("Amount exceeds 36 stacks (max " + maxAmount + ")").formatted(Formatting.RED));
            return 0;
        }
        r.amount = amount;
        orders.addRequest(r);
        long tax = Math.round(price * EconomyConfig.get().taxRate);

        Text msg = Text.literal("Created request" +
                (tax > 0 ? " (fulfiller receives " + EconomyCraft.formatMoney(price - tax) + ")" : ""))
                .formatted(Formatting.GREEN);
        player.sendMessage(msg);

        return 1;
    }

    private static int claimOrders(ServerPlayerEntity player, ServerCommandSource source) {
        OrdersUi.openClaims(player, EconomyCraft.getManager(source.getServer()));
        return 1;
    }

    // =====================================================================
    // === Daily reward ====================================================
    // =====================================================================

    private static LiteralArgumentBuilder<ServerCommandSource> buildDaily() {
        return literal("daily")
                .executes(ctx -> daily(ctx.getSource().getPlayerOrThrow(), ctx.getSource()));
    }

    private static int daily(ServerPlayerEntity player, ServerCommandSource source) {
        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        if (manager.claimDaily(player.getUuid())) {
            Text msg = Text.literal("Claimed " + EconomyCraft.formatMoney(EconomyConfig.get().dailyAmount))
                    .formatted(Formatting.GREEN);
            player.sendMessage(msg);
        } else {
            source.sendError(Text.literal("Already claimed today").formatted(Formatting.RED));
        }
        return 1;
    }

    // =====================================================================
    // === Helpers =========================================================
    // =====================================================================

    private static String getDisplayName(EconomyManager manager, UUID id) {
        var server = manager.getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) return IdentityCompat.of(online).name();
        String name = manager.getBestName(id);
        if (name != null && !name.isBlank()) return name;
        return id.toString();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(ServerCommandSource source, SuggestionsBuilder builder) {
        var server = source.getServer();
        var manager = EconomyCraft.getManager(server);
        Set<String> suggestions = new HashSet<>();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            suggestions.add(IdentityCompat.of(p).name());
        }

        for (UUID id : manager.getBalances().keySet()) {
            String name = manager.getBestName(id);
            if (name != null && !name.isBlank()) {
                suggestions.add(name);
            }
        }

        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestServerShopCategories(ServerCommandSource source, SuggestionsBuilder builder) {
        PriceRegistry prices = EconomyCraft.getManager(source.getServer()).getPrices();
        for (String cat : prices.buyCategories()) {
            builder.suggest(cat);
        }
        return builder.buildFuture();
    }
}
