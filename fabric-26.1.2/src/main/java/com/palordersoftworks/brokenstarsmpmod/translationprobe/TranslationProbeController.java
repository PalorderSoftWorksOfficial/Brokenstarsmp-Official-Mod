package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CheckHacks-style sign translation probing: batches of up to 3 hacks per sign, invisible placement,
 * control-line exploit-preventer detection, and aggregate command execution at the end of a run.
 */
public final class TranslationProbeController {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int JOIN_DELAY_TICKS = 60;
    private static final int LINES_PER_SIGN = 3;
    private static final int OPEN_SIGN_DELAY_TICKS = 1;

    private static volatile CheckHacksConfig fileConfig = CheckHacksConfig.createDefaultRegistry();
    private static volatile boolean runtimeEnabled = false;

    private static final ConcurrentHashMap<UUID, Integer> JOIN_AT_TICK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CheckRun> RUNS = new ConcurrentHashMap<>();
    private static final Set<UUID> JOIN_CHECKED = ConcurrentHashMap.newKeySet();

    private TranslationProbeController() {}

    public static void init(MinecraftServer server) {
        reloadConfig(server);
    }

    public static void reloadConfig(MinecraftServer server) {
        fileConfig = TranslationProbeStorage.load(server);
        runtimeEnabled = fileConfig.enabled;
        LOGGER.info("[BrokenStarSMP/CheckHacks] Config loaded enabled={} defaultGroup={} joinGroup={} registry={} timeout={} between={}",
                fileConfig.enabled,
                fileConfig.defaultCheckHacks.size(),
                fileConfig.autoCheckOnJoin.hacks.size(),
                fileConfig.hacks.size(),
                fileConfig.timeoutTicks,
                fileConfig.betweenSignTicks);
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

    private static boolean isBedrockPlayer(ServerPlayerEntity player) {
        CheckHacksConfig.Bedrock bedrock = fileConfig.bedrock;
        if (bedrock == null || !bedrock.enabled || bedrock.prefixes == null) {
            return false;
        }
        String name = player.getName().getString();
        for (String prefix : bedrock.prefixes) {
            if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProbeExempt(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }

        if (player.getCommandSource().getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(2)))) {
            return true;
        }

        final Set<String> exemptGroups = Set.of(
                "moderator",
                "contentcreator",
                "administrator",
                "developers",
                "manager",
                "owner"
        );

        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUuid());
            if (user == null) {
                return false;
            }

            String primary = user.getPrimaryGroup();
            if (primary != null && exemptGroups.contains(primary.toLowerCase(Locale.ROOT))) {
                return true;
            }

            for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
                String name = group.getName();
                if (name != null && exemptGroups.contains(name.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (IllegalStateException ignored) {
        }

        return false;
    }

    public static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        CheckHacksConfig cfg = fileConfig;
        if (cfg == null || !probesActive() || cfg.autoCheckOnJoin == null || !cfg.autoCheckOnJoin.enabled) {
            return;
        }
        if (cfg.autoCheckOnJoin.onlyFirstJoin && !JOIN_CHECKED.add(player.getUuid())) {
            return;
        }
        JOIN_AT_TICK.put(player.getUuid(), server.getTicks() + JOIN_DELAY_TICKS);
    }

    public static void clearPlayer(UUID playerId, MinecraftServer server) {
        JOIN_AT_TICK.remove(playerId);

        CheckRun run = RUNS.remove(playerId);
        if (run != null) {
            restoreCurrentBatch(server, run);
            run.waiting = null;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.closeHandledScreen();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (CheckRun run : RUNS.values()) {
            restoreCurrentBatch(server, run);
            run.waiting = null;
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
                    List<String> joinIds = fileConfig.resolveHackIds(fileConfig.autoCheckOnJoin.hacks);
                    startPlayerCheck(p, server, joinIds.isEmpty() ? null : joinIds);
                }
            }
        }

        for (UUID id : List.copyOf(RUNS.keySet())) {
            CheckRun run = RUNS.get(id);
            if (run == null) {
                continue;
            }

            if (run.waiting != null) {
                if (tick == run.waiting.openSignAtTick) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null && p.getEntityWorld() instanceof ServerWorld world) {
                        if (world.getBlockEntity(run.waiting.signPos) instanceof SignBlockEntity sign) {
                            TranslationProbeSignHelper.sendSignPackets(p, sign, run.waiting.signPos);
                        }
                    }
                } else if (tick > run.waiting.deadlineTick) {
                    handleTimeout(server, id, run);
                }
            } else if (run.batchIndex < run.batches.size() && tick >= run.resumeAtTick) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null) {
                    openBatch(p, server, run);
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

        WaitingBatch w = run.waiting;
        restoreCurrentBatch(server, run);
        run.waiting = null;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.closeHandledScreen();
        }

        for (HackRegistryEntry hack : w.batch) {
            run.results.put(hack.id, HackProbeResultState.PROTECTED);
            dispatchResult(player, run, hack, HackProbeResultState.PROTECTED, "", w, "No sign response before deadline");
        }

        LOGGER.warn("[BrokenStarSMP/CheckHacks] timeout player={} batchSize={}",
                player != null ? player.getName().getString() : playerId, w.batch.size());

        run.batchIndex++;
        scheduleNextBatchOrFinish(server, playerId, run);
    }

    /**
     * @return true if vanilla sign handling should be cancelled.
     */
    public static boolean tryConsumeSignPacket(ServerPlayerEntity player, UpdateSignC2SPacket packet) {
        CheckRun run = RUNS.get(player.getUuid());
        if (run == null || run.waiting == null) {
            return false;
        }

        WaitingBatch w = run.waiting;
        if (!packet.getPos().equals(w.signPos)) {
            return false;
        }

        run.waiting = null;

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            restoreCurrentBatch(server, run);
            player.closeHandledScreen();
        }

        String[] lines = packet.getText();
        String ctrl = lines.length > 3 && lines[3] != null ? lines[3] : "";
        boolean exploitPreventer = HackProbeClassifier.isExploitPreventer(ctrl);

        if (exploitPreventer) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] exploit-preventer player={} ctrl={}",
                    player.getName().getString(), ctrl.strip());
        }

        LOGGER.info("[BrokenStarSMP/CheckHacks] batch response player={} L0='{}' L1='{}' L2='{}' CTRL='{}'",
                player.getName().getString(),
                lineAt(lines, 0),
                lineAt(lines, 1),
                lineAt(lines, 2),
                ctrl.strip());

        for (int i = 0; i < w.batch.size(); i++) {
            HackRegistryEntry hack = w.batch.get(i);
            String resp = i < lines.length && lines[i] != null ? lines[i] : "";
            HackProbeResultState state = HackProbeClassifier.evaluate(resp, hack, exploitPreventer);
            run.results.put(hack.id, state);
            dispatchResult(player, run, hack, state, resp.strip(), w, detailFor(state));
            logResult(player.getName().getString(), hack, state, resp.strip());
        }

        run.batchIndex++;
        if (server != null) {
            scheduleNextBatchOrFinish(server, player.getUuid(), run);
        } else {
            RUNS.remove(player.getUuid());
        }

        return true;
    }

    private static String lineAt(String[] lines, int index) {
        return index < lines.length && lines[index] != null ? lines[index].strip() : "";
    }

    private static String detailFor(HackProbeResultState state) {
        return switch (state) {
            case DETECTED -> "Mod translation/key response matched detection rules";
            case NOT_DETECTED -> "Vanilla fallback or clean keybind response";
            case PROTECTED -> "Exploit protection, raw key echo, or no response";
            case SKIPPED -> "Skipped";
        };
    }

    private static void logResult(String playerName, HackRegistryEntry hack, HackProbeResultState state, String resp) {
        switch (state) {
            case DETECTED -> LOGGER.warn("[BrokenStarSMP/CheckHacks] {} -> DETECTED (resp='{}')", hack.displayName, truncate(resp));
            case NOT_DETECTED -> LOGGER.info("[BrokenStarSMP/CheckHacks] {} -> NOT_DETECTED (resp='{}')", hack.displayName, truncate(resp));
            case PROTECTED -> LOGGER.warn("[BrokenStarSMP/CheckHacks] {} -> PROTECTED (resp='{}')", hack.displayName, truncate(resp));
            default -> LOGGER.info("[BrokenStarSMP/CheckHacks] {} -> {}", hack.displayName, state);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    public static void startPlayerCheck(ServerPlayerEntity player, MinecraftServer server, String singleHackIdOrNull) {
        if (singleHackIdOrNull != null && !singleHackIdOrNull.isBlank()) {
            startPlayerCheck(player, server, List.of(singleHackIdOrNull.trim()));
            return;
        }
        startPlayerCheck(player, server, (List<String>) null);
    }

    public static void startPlayerCheck(ServerPlayerEntity player, MinecraftServer server, List<String> hackIdsOrNull) {
        if (!probesActive()) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (probes off)", player.getName().getString());
            return;
        }

        if (isBedrockPlayer(player)) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (bedrock prefix)", player.getName().getString());
            return;
        }

        if (isProbeExempt(player)) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (exempt)", player.getName().getString());
            return;
        }

        if (RUNS.containsKey(player.getUuid())) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (run active)", player.getName().getString());
            return;
        }

        CheckHacksConfig cfg = fileConfig;
        List<String> queue;
        if (hackIdsOrNull == null) {
            queue = cfg.resolveHackIds(cfg.defaultCheckHacks);
        } else if (hackIdsOrNull.isEmpty()) {
            queue = cfg.resolveHackIds(cfg.defaultCheckHacks);
        } else {
            List<String> requested = new ArrayList<>();
            for (String id : hackIdsOrNull) {
                if (id != null && !id.isBlank()) {
                    requested.add(id.trim());
                }
            }
            queue = cfg.resolveHackIds(requested);
        }

        if (queue.isEmpty()) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] empty queue for {}", player.getName().getString());
            return;
        }

        List<List<HackRegistryEntry>> batches = buildBatches(queue);
        if (batches.isEmpty()) {
            LOGGER.warn("[BrokenStarSMP/CheckHacks] no valid hacks for {}", player.getName().getString());
            return;
        }

        CheckRun run = new CheckRun(batches);
        RUNS.put(player.getUuid(), run);
        openBatch(player, server, run);
    }

    private static List<List<HackRegistryEntry>> buildBatches(List<String> hackIds) {
        List<HackRegistryEntry> entries = new ArrayList<>();
        for (String id : hackIds) {
            HackRegistryEntry entry = fileConfig.getHack(id);
            if (entry != null) {
                entries.add(entry);
            }
        }
        List<List<HackRegistryEntry>> batches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += LINES_PER_SIGN) {
            batches.add(new ArrayList<>(entries.subList(i, Math.min(i + LINES_PER_SIGN, entries.size()))));
        }
        return batches;
    }

    private static void openBatch(ServerPlayerEntity player, MinecraftServer server, CheckRun run) {
        if (run.batchIndex >= run.batches.size()) {
            finishCheck(server, player.getUuid(), run);
            return;
        }

        List<HackRegistryEntry> batch = run.batches.get(run.batchIndex);
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            RUNS.remove(player.getUuid());
            return;
        }

        BlockPos signPos = TranslationProbeSignHelper.findAirNear(player);
        if (signPos == null) {
            for (HackRegistryEntry hack : batch) {
                run.results.put(hack.id, HackProbeResultState.SKIPPED);
            }
            run.batchIndex++;
            run.resumeAtTick = server.getTicks() + Math.max(1, fileConfig.betweenSignTicks);
            LOGGER.warn("[BrokenStarSMP/CheckHacks] no air near player={}", player.getName().getString());
            return;
        }

        BlockPos supportPos = signPos.down();
        BlockState previousSignState = world.getBlockState(signPos);
        BlockState previousSupportState = world.getBlockState(supportPos);
        boolean placedBarrier = world.getBlockState(supportPos).isAir();

        if (placedBarrier && !world.setBlockState(supportPos, Blocks.BARRIER.getDefaultState(), 3)) {
            deferBatch(server, run);
            return;
        }

        float yaw = MathHelper.wrapDegrees(player.getYaw());
        int rotation = (int) Math.floor((yaw + 180.0F) * 16.0F / 360.0F) & 15;
        BlockState signState = Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, rotation);
        if (!world.setBlockState(signPos, signState, 3)) {
            if (placedBarrier) {
                world.setBlockState(supportPos, previousSupportState, 3);
            }
            deferBatch(server, run);
            return;
        }

        if (!(world.getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
            world.setBlockState(signPos, previousSignState, 3);
            if (placedBarrier) {
                world.setBlockState(supportPos, previousSupportState, 3);
            }
            deferBatch(server, run);
            return;
        }

        Text[] front = new Text[4];
        Text[] back = new Text[4];
        for (int i = 0; i < LINES_PER_SIGN; i++) {
            Text line = i < batch.size() ? probeLine(batch.get(i)) : Text.empty();
            front[i] = line;
            back[i] = line;
        }
        Text control = Text.keybind(HackProbeClassifier.CONTROL_KEYBIND);
        front[3] = control;
        back[3] = control;

        SignText signText = new SignText(front, back, DyeColor.BLACK, false);
        sign.setText(signText, true);
        sign.setEditor(player.getUuid());
        sign.markDirty();

        int start = server.getTicks();
        int deadline = start + Math.max(20, fileConfig.timeoutTicks);

        run.waiting = new WaitingBatch(
                batch,
                signPos,
                supportPos,
                previousSignState,
                previousSupportState,
                placedBarrier,
                start,
                deadline,
                start + OPEN_SIGN_DELAY_TICKS,
                world.getRegistryKey()
        );

        LOGGER.info("[BrokenStarSMP/CheckHacks] batch start player={} hacks={} pos={}",
                player.getName().getString(),
                batch.stream().map(h -> h.id).toList(),
                signPos.toShortString());
    }

    private static Text probeLine(HackRegistryEntry entry) {
        String key = entry.key == null ? "" : entry.key;
        return switch (entry.mode) {
            case METEOR, TRANSLATE -> Text.translatableWithFallback(key, entry.fallback());
            case KEYBIND -> Text.keybind(key);
        };
    }

    private static void deferBatch(MinecraftServer server, CheckRun run) {
        run.resumeAtTick = server.getTicks() + 20;
    }

    private static void dispatchResult(
            ServerPlayerEntity player,
            CheckRun run,
            HackRegistryEntry hack,
            HackProbeResultState state,
            String line,
            WaitingBatch w,
            String detail
    ) {
        if (player == null) {
            return;
        }
        HackProbeResult res = new HackProbeResult(
                player.getUuid(),
                player.getName().getString(),
                hack.id,
                hack.key,
                hack.mode,
                state,
                w.startTick,
                w.deadlineTick,
                line,
                detail,
                w.signPos.toImmutable(),
                new String[0]
        );
        TranslationProbeHooks.dispatch(res);
    }

    private static void scheduleNextBatchOrFinish(MinecraftServer server, UUID playerId, CheckRun run) {
        if (run.batchIndex >= run.batches.size()) {
            finishCheck(server, playerId, run);
            return;
        }
        run.resumeAtTick = server.getTicks() + Math.max(1, fileConfig.betweenSignTicks);
    }

    private static void finishCheck(MinecraftServer server, UUID playerId, CheckRun run) {
        RUNS.remove(playerId);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        maybeRunAggregateCommands(server, player, run);
    }

    private static void maybeRunAggregateCommands(MinecraftServer server, ServerPlayerEntity player, CheckRun run) {
        if (player == null) {
            return;
        }
        CheckHacksConfig cfg = fileConfig;
        boolean anyDetected = false;
        boolean anyProtected = false;
        boolean allClean = true;

        for (List<HackRegistryEntry> batch : run.batches) {
            for (HackRegistryEntry hack : batch) {
                HackProbeResultState state = run.results.getOrDefault(hack.id, HackProbeResultState.SKIPPED);
                if (state == HackProbeResultState.DETECTED) {
                    anyDetected = true;
                    allClean = false;
                }
                if (state == HackProbeResultState.PROTECTED) {
                    anyProtected = true;
                    allClean = false;
                }
                if (state == HackProbeResultState.SKIPPED) {
                    allClean = false;
                }
            }
        }

        String name = player.getGameProfile().name();

        if (anyDetected && cfg.commandIfPositive.enabled && cfg.commandIfPositive.command != null
                && !cfg.commandIfPositive.command.isBlank()) {
            runCommand(server, cfg.commandIfPositive.command.replace("%player%", name), player, "positive");
        } else if (anyProtected && cfg.commandIfProtected.enabled && cfg.commandIfProtected.command != null
                && !cfg.commandIfProtected.command.isBlank()) {
            runCommand(server, cfg.commandIfProtected.command.replace("%player%", name), player, "protected");
        } else if (allClean && cfg.commandIfClean.enabled && cfg.commandIfClean.command != null
                && !cfg.commandIfClean.command.isBlank()) {
            runCommand(server, cfg.commandIfClean.command.replace("%player%", name), player, "clean");
        }
    }

    private static void runCommand(MinecraftServer server, String cmd, ServerPlayerEntity player, String kind) {
        LOGGER.info("[BrokenStarSMP/CheckHacks] exec player={} kind={} cmd={}",
                player.getName().getString(), kind, cmd);
        server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), cmd);
    }

    private static void restoreCurrentBatch(MinecraftServer server, CheckRun run) {
        WaitingBatch w = run.waiting;
        if (w == null) {
            return;
        }
        ServerWorld world = server.getWorld(w.worldKey);
        if (world != null) {
            world.setBlockState(w.signPos, w.previousSignState, 3);
            if (w.placedBarrier) {
                world.setBlockState(w.supportPos, w.previousSupportState, 3);
            }
        }
    }

    private static final class CheckRun {
        final List<List<HackRegistryEntry>> batches;
        final Map<String, HackProbeResultState> results = new LinkedHashMap<>();
        int batchIndex;
        int resumeAtTick;
        WaitingBatch waiting;

        CheckRun(List<List<HackRegistryEntry>> batches) {
            this.batches = batches;
            this.batchIndex = 0;
            this.resumeAtTick = 0;
        }
    }

    private static final class WaitingBatch {
        final List<HackRegistryEntry> batch;
        final BlockPos signPos;
        final BlockPos supportPos;
        final BlockState previousSignState;
        final BlockState previousSupportState;
        final boolean placedBarrier;
        final int startTick;
        final int deadlineTick;
        final int openSignAtTick;
        final RegistryKey<World> worldKey;

        WaitingBatch(
                List<HackRegistryEntry> batch,
                BlockPos signPos,
                BlockPos supportPos,
                BlockState previousSignState,
                BlockState previousSupportState,
                boolean placedBarrier,
                int startTick,
                int deadlineTick,
                int openSignAtTick,
                RegistryKey<World> worldKey
        ) {
            this.batch = batch;
            this.signPos = signPos.toImmutable();
            this.supportPos = supportPos.toImmutable();
            this.previousSignState = previousSignState;
            this.previousSupportState = previousSupportState;
            this.placedBarrier = placedBarrier;
            this.startTick = startTick;
            this.deadlineTick = deadlineTick;
            this.openSignAtTick = openSignAtTick;
            this.worldKey = worldKey;
        }
    }
}
