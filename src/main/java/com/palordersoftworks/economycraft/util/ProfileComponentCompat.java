package com.palordersoftworks.economycraft.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.component.type.ProfileComponent;

import java.util.Optional;
import java.util.UUID;

public final class ProfileComponentCompat {
    private ProfileComponentCompat() {}

    public static Optional<ProfileComponent> tryResolvedOrUnresolved(GameProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }
        return Optional.of(ProfileComponent.ofStatic(profile));
    }

    public static Optional<ProfileComponent> tryUnresolved(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.of(ProfileComponent.ofDynamic(""));
        }
        String trimmed = nameOrId.trim();
        try {
            return Optional.of(ProfileComponent.ofDynamic(UUID.fromString(trimmed)));
        } catch (IllegalArgumentException ignored) {
            return Optional.of(ProfileComponent.ofDynamic(trimmed));
        }
    }
}
