package com.palordersoftworks.brokenstarsmpmod.framework.config;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ReloadResult(
        boolean successful,
        Set<String> loadedFiles,
        int loadedRules,
        List<ConfigIssue> issues,
        Set<String> restartRequired,
        String activeProfile,
        Instant lastReload,
        int configVersion
) {
}
