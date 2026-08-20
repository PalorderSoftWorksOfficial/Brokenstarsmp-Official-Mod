package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Outcome of one hack probe (one sign interaction).
 */
public record HackProbeResult(
        UUID playerUuid,
        String playerName,
        String hackId,
        String expectedKey,
        HackProbeMode mode,
        HackProbeResultState state,
        int startTick,
        int deadlineTick,
        String lastResponseLine,
        String detail,
        BlockPos signPos,
        String[] submittedLines
) {}
