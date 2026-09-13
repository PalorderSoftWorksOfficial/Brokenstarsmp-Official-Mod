package com.palordersoftworks.brokenstarsmpmod.rankshop;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the official LuckPerms API (no console commands).
 *
 * Group names are passed through exactly as configured; no lowercasing or
 * normalization happens anywhere in this class.
 */
public final class LuckPermsService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LuckPermsService() {}

    /**
     * Grants {@code group} to the player, optionally for a limited duration, and
     * removes the nodes of every group in {@code replaces} plus any previous node
     * for {@code group} itself.
     *
     * @return true if LuckPerms accepted the change
     */
    public static boolean grant(UUID player, String group, Duration duration, List<String> replaces) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            UserManager users = luckPerms.getUserManager();
            users.modifyUser(player, user -> {
                if (replaces != null && !replaces.isEmpty()) {
                    user.data().clear(node -> isGroup(node, replaces));
                }
                // Replace any previous node for this group so renewals cannot stack.
                user.data().clear(node -> isGroup(node, List.of(group)));

                InheritanceNode.Builder builder = InheritanceNode.builder(group);
                if (duration != null) {
                    builder.expiry(duration);
                }
                user.data().add(builder.build());
            }).orTimeout(10, TimeUnit.SECONDS).join();
            return true;
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] LuckPerms grant failed for {} -> group '{}'", player, group, e);
            return false;
        }
    }

    /** Removes the configured group entirely (admin take/unrank support). */
    public static boolean revoke(UUID player, String group) {
        try {
            LuckPermsProvider.get().getUserManager().modifyUser(player, user ->
                    user.data().clear(node -> isGroup(node, List.of(group)))
            ).orTimeout(10, TimeUnit.SECONDS).join();
            return true;
        } catch (Exception e) {
            LOGGER.error("[BrokenStars] LuckPerms revoke failed for {} -> group '{}'", player, group, e);
            return false;
        }
    }

    private static boolean isGroup(Node node, List<String> groups) {
        return node instanceof InheritanceNode inheritanceNode
                && groups.contains(inheritanceNode.getGroupName());
    }
}
