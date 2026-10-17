package com.palordersoftworks.brokenstarsmpmod.framework.permissions;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class LpHook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentMap<String, ContextCalculator<net.minecraft.server.level.ServerPlayer>> CALCULATORS =
            new ConcurrentHashMap<>();
    private static volatile boolean broken;

    private LpHook() {
    }

    public static boolean available() {
        if (broken) {
            return false;
        }
        try {
            get();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static LuckPerms get() {
        return LuckPermsProvider.get();
    }

    public static User user(UUID uuid) {
        if (uuid == null || !available()) {
            return null;
        }
        try {
            return get().getUserManager().getUser(uuid);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static boolean check(UUID uuid, String node, QueryOptions options) {
        User user = user(uuid);
        if (user == null || node == null || node.isBlank()) {
            return false;
        }
        try {
            return user.getCachedData().getPermissionData(options).checkPermission(node).asBoolean();
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean check(UUID uuid, String node) {
        User user = user(uuid);
        if (user == null || node == null || node.isBlank()) {
            return false;
        }
        try {
            return user.getCachedData().getPermissionData(user.getQueryOptions()).checkPermission(node).asBoolean();
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static Optional<String> meta(UUID uuid, String key) {
        User user = user(uuid);
        if (user == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(user.getCachedData().getMetaData(user.getQueryOptions()).getMetaValue(key));
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public static Optional<String> primaryGroup(UUID uuid) {
        User user = user(uuid);
        if (user == null) {
            return Optional.empty();
        }
        try {
            String group = user.getPrimaryGroup();
            if (group == null || group.isBlank()) {
                return Optional.empty();
            }
            Group loaded = get().getGroupManager().getGroup(group);
            return Optional.ofNullable(loaded == null ? group : loaded.getName());
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public static Optional<String> prefix(UUID uuid) {
        User user = user(uuid);
        if (user == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(user.getCachedData().getMetaData().getPrefix());
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public static Optional<String> suffix(UUID uuid) {
        User user = user(uuid);
        if (user == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(user.getCachedData().getMetaData().getSuffix());
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public static Optional<QueryOptions> queryOptions(UUID uuid) {
        User user = user(uuid);
        if (user == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(user.getQueryOptions());
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public static boolean registerContextCalculator(String id, String contextKey,
                                                    Function<net.minecraft.server.level.ServerPlayer, String> value) {
        if (!available()) {
            return false;
        }
        try {
            ContextCalculator<net.minecraft.server.level.ServerPlayer> calculator = new ContextCalculator<>() {
                @Override
                public void calculate(net.minecraft.server.level.ServerPlayer target, ContextConsumer consumer) {
                    String applied = value.apply(target);
                    if (applied != null && !applied.isBlank()) {
                        consumer.accept(contextKey, applied);
                    }
                }

                @Override
                public ContextSet estimatePotentialContexts() {
                    return ImmutableContextSet.of(contextKey, "*");
                }
            };
            get().getContextManager().registerCalculator(calculator);
            CALCULATORS.put(id, calculator);
            LOGGER.info("[BrokenStarSMP/LuckPerms] Registered context calculator {} ({})", contextKey, id);
            return true;
        } catch (Throwable throwable) {
            broken = true;
            LOGGER.warn("[BrokenStarSMP/LuckPerms] Context calculator registration failed for {}", contextKey, throwable);
            return false;
        }
    }

    public static void unregisterContextCalculator(String id) {
        ContextCalculator<net.minecraft.server.level.ServerPlayer> calculator = CALCULATORS.remove(id);
        if (calculator == null) {
            return;
        }
        try {
            get().getContextManager().unregisterCalculator(calculator);
        } catch (Throwable ignored) {
        }
    }

    public static void signalContextUpdate(net.minecraft.server.level.ServerPlayer player) {
        if (!available() || player == null) {
            return;
        }
        try {
            get().getContextManager().signalContextUpdate(player);
        } catch (Throwable ignored) {
        }
    }

    public static void unregisterAll() {
        for (String id : CALCULATORS.keySet()) {
            unregisterContextCalculator(id);
        }
    }
}
