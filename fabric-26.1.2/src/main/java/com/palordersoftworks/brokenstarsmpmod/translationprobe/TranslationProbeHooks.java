package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Optional integration for moderation; receives one {@link HackProbeResult} per completed sign probe.
 */
public final class TranslationProbeHooks {
    private static final List<Consumer<HackProbeResult>> LISTENERS = new CopyOnWriteArrayList<>();

    private TranslationProbeHooks() {}

    public static void registerListener(Consumer<HackProbeResult> listener) {
        LISTENERS.add(listener);
    }

    public static void unregisterListener(Consumer<HackProbeResult> listener) {
        LISTENERS.remove(listener);
    }

    static void dispatch(HackProbeResult result) {
        for (Consumer<HackProbeResult> listener : LISTENERS) {
            try {
                listener.accept(result);
            } catch (Throwable t) {
                LogUtils.getLogger().warn("[BrokenStarSMP/CheckHacks] Listener threw for hack {}", result.hackId(), t);
            }
        }
    }
}
