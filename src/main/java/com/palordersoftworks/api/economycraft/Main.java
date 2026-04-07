package com.palordersoftworks.api.economycraft;

import com.palordersoftworks.economycraft.EconomyManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Main {

    private final EconomyManager manager;

    public Main(EconomyManager manager) {
        this.manager = manager;
    }

    public long getBalance(UUID player) {
        return manager.getBalance(player, true);
    }

    public void setBalance(UUID player, long amount) {
        manager.setMoney(player, amount);
    }

    public void addBalance(UUID player, long amount) {
        manager.addMoney(player, amount);
    }

    public boolean removeBalance(UUID player, long amount) {
        return manager.removeMoney(player, amount);
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        return manager.pay(from, to, amount);
    }

    public long getShards(UUID player) {
        return manager.getShards(player, true);
    }

    public void setShards(UUID player, long amount) {
        manager.setShards(player, amount);
    }

    public void addShards(UUID player, long amount) {
        manager.addShards(player, amount);
    }

    public boolean removeShards(UUID player, long amount) {
        return manager.removeShards(player, amount);
    }

    public boolean shardPay(UUID from, UUID to, long amount) {
        return manager.shardPay(from, to, amount);
    }

    @Nullable
    public String offerCoinflip(ServerPlayerEntity challenger, ServerPlayerEntity target, long amount) {
        return manager.tryOfferCoinflip(challenger, target, amount);
    }

    @Nullable
    public EconomyManager.CoinflipAcceptResult acceptCoinflip(ServerPlayerEntity target) {
        return manager.tryAcceptCoinflip(target);
    }

    @Nullable
    public String cancelCoinflip(ServerPlayerEntity challenger) {
        return manager.tryCancelCoinflip(challenger);
    }

    @Nullable
    public EconomyManager.CoinflipOffer peekCoinflip(ServerPlayerEntity target) {
        return manager.peekCoinflipFor(target);
    }

    public boolean claimDaily(UUID player) {
        return manager.claimDaily(player);
    }

    public boolean recordDailySell(UUID player, long amount) {
        return manager.tryRecordDailySell(player, amount);
    }

    public long dailySellRemaining(UUID player) {
        return manager.getDailySellRemaining(player);
    }

    public boolean markBanknoteRedeemed(String signature) {
        return manager.markBanknoteRedeemed(signature);
    }

    public Map<UUID, Long> getBalances() {
        return manager.getBalances();
    }

    public Map<UUID, Long> getShardBalances() {
        return manager.getShardBalances();
    }

    public String getBestName(UUID player) {
        return manager.getBestName(player);
    }

    @Nullable
    public UUID resolveUuid(String name) {
        return manager.tryResolveUuidByName(name);
    }

    public void handlePvpKill(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        manager.handlePvpKill(victim, killer);
    }

    public boolean toggleScoreboard() {
        return manager.toggleScoreboard();
    }

    public void removePlayer(UUID id) {
        manager.removePlayer(id);
    }
}