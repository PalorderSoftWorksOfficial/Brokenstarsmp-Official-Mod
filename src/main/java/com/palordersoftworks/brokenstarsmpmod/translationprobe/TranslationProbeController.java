package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs sign-based checks for hacks listed in {@link CheckHacksConfig#defaultCheckHacks} only.
 */
public final class TranslationProbeController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int JOIN_DELAY_TICKS = 100;
    private static final int LINE_INDEX = 0;

    private static volatile CheckHacksConfig fileConfig = CheckHacksConfig.createDefaultRegistry();
    private static volatile boolean runtimeEnabled = false;

    private static final ConcurrentHashMap<UUID, Integer> JOIN_AT_TICK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CheckRun> RUNS = new ConcurrentHashMap<>();

    private TranslationProbeController() {}

    public static void init(MinecraftServer server) {
        reloadConfig(server);
    }

    public static void reloadConfig(MinecraftServer server) {
        fileConfig = TranslationProbeStorage.load(server);
        runtimeEnabled = fileConfig.enabled;
        LOGGER.info("[BrokenStarSMP/CheckHacks] Config loaded enabled={} groupSize={} registrySize={} detectFlag={}",
                fileConfig.enabled,
                fileConfig.defaultCheckHacks.size(),
                fileConfig.hacks.size(),
                fileConfig.detectFlag);
    }

    public static void setRuntimeEnabled(boolean enabled) {
        runtimeEnabled = enabled;
    }

    public static boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    public static CheckHacksConfig getFileConfig() {
        return fileConfig;
    }

    private static boolean probesActive() {
        return runtimeEnabled && fileConfig != null && fileConfig.enabled;
    }

    public static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        CheckHacksConfig cfg = fileConfig;
        if (cfg == null || !probesActive() || !cfg.autoCheckOnJoin) {
            return;
        }
        JOIN_AT_TICK.put(player.getUuid(), server.getTicks() + JOIN_DELAY_TICKS);
    }

    public static void clearPlayer(UUID playerId, MinecraftServer server) {
        JOIN_AT_TICK.remove(playerId);
        CheckRun run = RUNS.remove(playerId);
        if (run != null && run.waiting != null) {
            restore(server, run.waiting);
            run.waiting = null;
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (CheckRun run : RUNS.values()) {
            if (run.waiting != null) {
                restore(server, run.waiting);
            }
        }
        RUNS.clear();
        JOIN_AT_TICK.clear();
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTicks();
        for (var it = JOIN_AT_TICK.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (tick >= e.getValue()) {
                it.remove();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    startPlayerCheck(p, server, null);
                }
            }
        }
        for (UUID id : List.copyOf(RUNS.keySet())) {
            CheckRun run = RUNS.get(id);
            if (run == null) {
                continue;
            }
            if (run.waiting != null && tick > run.waiting.deadlineTick) {
                handleTimeout(server, id, run);
            } else if (run.waiting == null && run.index < run.queue.size() && tick >= run.resumeAtTick) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null) {
                    openSignForCurrentHack(p, server, run);
                } else {
                    RUNS.remove(id);
                }
            }
        }
    }

    private static void handleTimeout(MinecraftServer server, UUID playerId, CheckRun run) {
        if (run.waiting == null) {
            return;
        }
        WaitingSign w = run.waiting;
        restore(server, w);
        run.waiting = null;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        String name = player != null ? player.getName().getString() : playerId.toString();
        HackProbeResult res = new HackProbeResult(
                playerId,
                name,
                w.hackId,
                w.expectedKey,
                w.mode,
                HackProbeResultState.TIMEOUT,
                w.startTick,
                w.deadlineTick,
                "",
                "No sign response before deadline",
                w.pos.toImmutable(),
                new String[0]
        );
        LOGGER.warn("[BrokenStarSMP/CheckHacks] timeout player={} hack={} key={}", name, w.hackId, w.expectedKey);
        afterSingleHack(server, player, run, res);
    }

    /**
     * @return true if vanilla sign handling should be cancelled.
     */
    public static boolean tryConsumeSignPacket(ServerPlayerEntity player, UpdateSignC2SPacket packet) {
        CheckRun run = RUNS.get(player.getUuid());
        if (run == null || run.waiting == null) {
            return false;
        }
        WaitingSign w = run.waiting;
        if (!packet.getPos().equals(w.pos)) {
            return false;
        }
        run.waiting = null;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            restore(server, w);
        }

        String[] lines = packet.getText();
        String line = LINE_INDEX < lines.length && lines[LINE_INDEX] != null ? lines[LINE_INDEX] : "";
        HackProbeResultState state = HackProbeClassifier.classifyLine(line, w.expectedKey, w.mode, w.displayName);
        String detail = switch (state) {
            case CLEAN -> "Probe line matched expected vanilla handling";
            case FLAGGED -> "Probe line mismatch";
            case PROTECTED -> "Blank or stripped probe line";
            case TIMEOUT -> "";
        };
        HackProbeResult res = new HackProbeResult(
                player.getUuid(),
                player.getName().getString(),
                w.hackId,
                w.expectedKey,
                w.mode,
                state,
                w.startTick,
                w.deadlineTick,
                line,
                detail,
                w.pos.toImmutable(),
                lines.clone()
        );
        switch (state) {
            case CLEAN -> LOGGER.info("[BrokenStarSMP/CheckHacks] success player={} hack={} line={}",
                    res.playerName(), res.hackId(), truncate(res.lastResponseLine()));
            case FLAGGED -> LOGGER.warn("[BrokenStarSMP/CheckHacks] mismatch player={} hack={} line={}",
                    res.playerName(), res.hackId(), truncate(res.lastResponseLine()));
            case PROTECTED -> LOGGER.warn("[BrokenStarSMP/CheckHacks] protected player={} hack={}",
                    res.playerName(), res.hackId());
            default -> {
            }
        }
        if (server != null) {
            afterSingleHack(server, player, run, res);
        }
        return true;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    public static void startPlayerCheck(ServerPlayerEntity player, MinecraftServer server, String singleHackIdOrNull) {
        if (!probesActive()) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (probes off)", player.getName().getString());
            return;
        }
        if (RUNS.containsKey(player.getUuid())) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (run active)", player.getName().getString());
            return;
        }
        CheckHacksConfig cfg = fileConfig;
        List<String> queue = new ArrayList<>();
        if (singleHackIdOrNull != null && !singleHackIdOrNull.isBlank()) {
            String hid = singleHackIdOrNull.trim();
            if (!cfg.defaultCheckHacks.contains(hid)) {
                LOGGER.warn("[BrokenStarSMP/CheckHacks] hack {} not in default-check-hacks for {}", hid, player.getName().getString());
                return;
            }
            if (cfg.getHack(hid) == null) {
                LOGGER.warn("[BrokenStarSMP/CheckHacks] unknown hack id {}", hid);
                return;
            }
            queue.add(hid);
        } else {
            for (String id : cfg.defaultCheckHacks) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (cfg.getHack(id) != null) {
                    queue.add(id);
                }
            }
        }
        if (queue.isEmpty()) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] empty queue for {}", player.getName().getString());
            return;
        }
        CheckRun run = new CheckRun(queue);
        RUNS.put(player.getUuid(), run);
        openSignForCurrentHack(player, server, run);
    }

    private static void openSignForCurrentHack(ServerPlayerEntity player, MinecraftServer server, CheckRun run) {
        if (run.index >= run.queue.size()) {
            RUNS.remove(player.getUuid());
            return;
        }
        String hackId = run.queue.get(run.index);
        HackRegistryEntry entry = fileConfig.getHack(hackId);
        if (entry == null) {
            run.index++;
            scheduleNextHack(server, player, run, 0);
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            RUNS.remove(player.getUuid());
            return;
        }
        BlockPos pos = findPlacement(player, world);
        if (pos == null) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] no sign space player={} hack={}", player.getName().getString(), hackId);
            RUNS.remove(player.getUuid());
            return;
        }
        BlockState previous = world.getBlockState(pos);
        float yaw = MathHelper.wrapDegrees(player.getYaw());
        int rotation = (int) Math.floor((yaw + 180.0F) * 16.0F / 360.0F) & 15;
        BlockState signState = Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, rotation);
        if (!signState.canPlaceAt(world, pos) || !world.setBlockState(pos, signState, 3)) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] sign place failed player={} hack={}", player.getName().getString(), hackId);
            RUNS.remove(player.getUuid());
            world.setBlockState(pos, previous, 3);
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            world.setBlockState(pos, previous, 3);
            RUNS.remove(player.getUuid());
            return;
        }
        String key = entry.key == null ? "" : entry.key;
        Text[] messages = new Text[]{Text.translatable(key), Text.empty(), Text.empty(), Text.empty()};
        SignText signText = new SignText(messages, messages, DyeColor.BLACK, false);
        sign.setText(signText, true);
        sign.setEditor(player.getUuid());
        sign.markDirty();

        int start = server.getTicks();
        int deadline = start + Math.max(20, fileConfig.timeoutTicks);
        run.waiting = new WaitingSign(
                hackId,
                key,
                entry.mode,
                entry.displayName == null ? "" : entry.displayName,
                pos,
                previous,
                start,
                deadline,
                world.getRegistryKey()
        );
        player.openEditSignScreen(sign, true);
        LOGGER.info("[BrokenStarSMP/CheckHacks] start player={} hack={} key={}", player.getName().getString(), hackId, key);
    }

    private static void afterSingleHack(MinecraftServer server, ServerPlayerEntity player, CheckRun run, HackProbeResult res) {
        maybeRunCommand(server, player, res);
        TranslationProbeHooks.dispatch(res);
        run.index++;
        int gap = Math.max(0, fileConfig.betweenSignTicks);
        scheduleNextHack(server, player, run, gap);
    }

    private static void scheduleNextHack(MinecraftServer server, ServerPlayerEntity player, CheckRun run, int gapTicks) {
        if (run.index >= run.queue.size()) {
            RUNS.remove(player.getUuid());
            return;
        }
        run.resumeAtTick = server.getTicks() + gapTicks;
        if (gapTicks <= 0) {
            openSignForCurrentHack(player, server, run);
        }
    }

    private static void maybeRunCommand(MinecraftServer server, ServerPlayerEntity player, HackProbeResult res) {
        CheckHacksConfig cfg = fileConfig;
        if (!cfg.detectFlag || player == null) {
            return;
        }
        String template = switch (res.state()) {
            case CLEAN -> cfg.commandIfClean;
            case FLAGGED -> cfg.commandIfPositive;
            case PROTECTED -> cfg.commandIfProtected;
            case TIMEOUT -> "";
        };
        if (template == null || template.isBlank()) {
            return;
        }
        String expanded = template.replace("%player%", player.getGameProfile().name());
        LOGGER.info("[BrokenStarSMP/CheckHacks] exec player={} hack={} state={} cmd={}",
                res.playerName(), res.hackId(), res.state(), expanded);
        server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), expanded);
    }

    private static void restore(MinecraftServer server, WaitingSign w) {
        ServerWorld world = server.getWorld(w.worldKey);
        if (world != null) {
            world.setBlockState(w.pos, w.previousState, 3);
        }
    }

    private static BlockPos findPlacement(ServerPlayerEntity player, ServerWorld world) {
        BlockPos base = player.getBlockPos();
        int[][] offs = {
                {0, 3, 0}, {0, 4, 0}, {0, 5, 0}, {0, 6, 0}, {0, 7, 0}, {0, 8, 0},
                {1, 3, 0}, {-1, 3, 0}, {0, 3, 1}, {0, 3, -1},
                {1, 4, 0}, {-1, 4, 0}, {0, 4, 1}, {0, 4, -1}
        };
        for (int[] o : offs) {
            BlockPos p = base.add(o[0], o[1], o[2]);
            if (canPlaceSignHere(world, p)) {
                return p.toImmutable();
            }
        }
        return null;
    }

    private static boolean canPlaceSignHere(ServerWorld world, BlockPos pos) {
        BlockState at = world.getBlockState(pos);
        if (!at.isReplaceable()) {
            return false;
        }
        BlockState below = world.getBlockState(pos.down());
        return below.isSolidBlock(world, pos.down());
    }

    private static final class CheckRun {
        final List<String> queue;
        int index;
        int resumeAtTick;
        WaitingSign waiting;

        CheckRun(List<String> queue) {
            this.queue = queue;
            this.index = 0;
            this.resumeAtTick = 0;
        }
    }

    private static final class WaitingSign {
        final String hackId;
        final String expectedKey;
        final HackProbeMode mode;
        final String displayName;
        final BlockPos pos;
        final BlockState previousState;
        final int startTick;
        final int deadlineTick;
        final RegistryKey<World> worldKey;

        WaitingSign(
                String hackId,
                String expectedKey,
                HackProbeMode mode,
                String displayName,
                BlockPos pos,
                BlockState previousState,
                int startTick,
                int deadlineTick,
                RegistryKey<World> worldKey
        ) {
            this.hackId = hackId;
            this.expectedKey = expectedKey;
            this.mode = mode;
            this.displayName = displayName;
            this.pos = pos.toImmutable();
            this.previousState = previousState;
            this.startTick = startTick;
            this.deadlineTick = deadlineTick;
            this.worldKey = worldKey;
        }
    }
}
