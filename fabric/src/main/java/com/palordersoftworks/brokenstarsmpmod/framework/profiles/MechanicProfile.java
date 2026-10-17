package com.palordersoftworks.brokenstarsmpmod.framework.profiles;

import java.util.Map;
import java.util.Objects;

public record MechanicProfile(
        String id,
        String extendsProfile,
        Map<String, Object> overrides,
        String description
) {
    public MechanicProfile {
        id = Objects.requireNonNull(id, "id");
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        extendsProfile = extendsProfile == null || extendsProfile.isBlank() ? null : extendsProfile;
        description = description == null ? "" : description;
    }
}
