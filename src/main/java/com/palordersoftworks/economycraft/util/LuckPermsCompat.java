package com.palordersoftworks.economycraft.util;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional LuckPerms integration via reflection (no compile-time dependency on LP).
 * Use the meta key from {@link com.palordersoftworks.economycraft.EconomyConfig#playerVaultLuckPermsMetaKey}
 * (default {@code brokenstarsmp.economy.playervault.amount}) with an integer value for vault count.
 */
public final class LuckPermsCompat {
    private LuckPermsCompat() {}

    public static boolean isLuckPermsPresent() {
        return FabricLoader.getInstance().isModLoaded("luckperms");
    }

    /**
     * Reads a string meta value from LuckPerms' cached user data, if available.
     */
    public static Optional<String> getMetaValue(UUID playerId, String metaKey) {
        if (!isLuckPermsPresent() || metaKey == null || metaKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Class<?> providerCls = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerCls.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerId);
            if (user == null) {
                return Optional.empty();
            }
            Object cached = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cached.getClass().getMethod("getMetaData").invoke(cached);
            Object val = metaData.getClass().getMethod("getMetaValue", String.class).invoke(metaData, metaKey);
            if (val == null) {
                return Optional.empty();
            }
            String s = val.toString().trim();
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}
