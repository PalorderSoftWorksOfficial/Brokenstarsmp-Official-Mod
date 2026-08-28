package com.palordersoftworks.brokenstarsmpmod.translationprobe;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TranslationProbeController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LINES_PER_SIGN = 3;

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
        LOGGER.info("[BrokenStarSMP/CheckHacks] Config loaded enabled={} defaultGroup={} joinGroup={} registry={} joinDelay={} timeout={} openDelay={} between={}",
                fileConfig.enabled,
                fileConfig.defaultCheckHacks.size(),
                fileConfig.autoCheckOnJoin.hacks.size(),
                fileConfig.hacks.size(),
                fileConfig.joinDelayTicks,
                fileConfig.timeoutTicks,
                fileConfig.openSignDelayTicks,
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

    private static boolean isBedrockPlayer(ServerPlayer player) {
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

    private static boolean isProbeExempt(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        if (player.createCommandSourceStack().permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2)))) {
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
            User user = lp.getUserManager().getUser(player.getUUID());
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

    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        CheckHacksConfig cfg = fileConfig;
        if (cfg == null || !probesActive() || cfg.autoCheckOnJoin == null || !cfg.autoCheckOnJoin.enabled) {
            return;
        }
        if (cfg.autoCheckOnJoin.onlyFirstJoin && !JOIN_CHECKED.add(player.getUUID())) {
            return;
        }

        int delay = Math.max(0, cfg.joinDelayTicks);
        if (delay == 0) {
            List<String> joinIds = cfg.resolveHackIds(cfg.autoCheckOnJoin.hacks);
            startPlayerCheck(player, server, joinIds.isEmpty() ? null : joinIds);
            return;
        }

        JOIN_AT_TICK.put(player.getUUID(), server.getTickCount() + delay);
    }

    public static void clearPlayer(UUID playerId, MinecraftServer server) {
        JOIN_AT_TICK.remove(playerId);

        CheckRun run = RUNS.remove(playerId);
        if (run != null) {
            clearCurrentVirtualSign(server, playerId, run);
            run.waiting = null;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.closeContainer();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (Map.Entry<UUID, CheckRun> entry : RUNS.entrySet()) {
            clearCurrentVirtualSign(server, entry.getKey(), entry.getValue());
            entry.getValue().waiting = null;
        }
        RUNS.clear();
        JOIN_AT_TICK.clear();
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();

        for (var it = JOIN_AT_TICK.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (tick >= e.getValue()) {
                it.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
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
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) {
                        TranslationProbeSignHelper.sendSignPackets(p, run.waiting.sign, run.waiting.signPos);
                    }
                } else if (tick > run.waiting.deadlineTick) {
                    handleTimeout(server, id, run);
                }
            } else if (run.batchIndex < run.batches.size() && tick >= run.resumeAtTick) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
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
        clearCurrentVirtualSign(server, playerId, run);
        run.waiting = null;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.closeContainer();
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

    public static boolean tryConsumeSignPacket(ServerPlayer player, ServerboundSignUpdatePacket packet) {
        CheckRun run = RUNS.get(player.getUUID());
        if (run == null || run.waiting == null) {
            return false;
        }

        WaitingBatch w = run.waiting;
        if (!packet.getPos().equals(w.signPos)) {
            return false;
        }

        run.waiting = null;

        MinecraftServer server = player.level().getServer();
        if (server != null) {
            clearCurrentVirtualSign(server, player.getUUID(), run);
            player.closeContainer();
        }

        String[] lines = packet.getLines();
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
            scheduleNextBatchOrFinish(server, player.getUUID(), run);
        } else {
            RUNS.remove(player.getUUID());
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

    public static void startPlayerCheck(ServerPlayer player, MinecraftServer server, String singleHackIdOrNull) {
        if (singleHackIdOrNull != null && !singleHackIdOrNull.isBlank()) {
            startPlayerCheck(player, server, List.of(singleHackIdOrNull.trim()));
            return;
        }
        startPlayerCheck(player, server, (List<String>) null);
    }

    public static void startPlayerCheck(ServerPlayer player, MinecraftServer server, List<String> hackIdsOrNull) {
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

        if (RUNS.containsKey(player.getUUID())) {
            LOGGER.debug("[BrokenStarSMP/CheckHacks] skip player={} (run active)", player.getName().getString());
            return;
        }

        CheckHacksConfig cfg = fileConfig;
        List<String> queue;
        if (hackIdsOrNull == null || hackIdsOrNull.isEmpty()) {
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
        RUNS.put(player.getUUID(), run);
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

    private static void openBatch(ServerPlayer player, MinecraftServer server, CheckRun run) {
        if (run.batchIndex >= run.batches.size()) {
            finishCheck(server, player.getUUID(), run);
            return;
        }

        List<HackRegistryEntry> batch = run.batches.get(run.batchIndex);
        if (!(player.level() instanceof ServerLevel world)) {
            RUNS.remove(player.getUUID());
            return;
        }

        BlockPos signPos = TranslationProbeSignHelper.findAirNear(player);
        if (signPos == null) {
            for (HackRegistryEntry hack : batch) {
                run.results.put(hack.id, HackProbeResultState.SKIPPED);
            }
            run.batchIndex++;
            run.resumeAtTick = server.getTickCount() + Math.max(0, fileConfig.betweenSignTicks);
            return;
        }

        float yaw = Mth.wrapDegrees(player.getYRot());
        int rotation = (int) Math.floor((yaw + 180.0F) * 16.0F / 360.0F) & 15;
        BlockState signState = Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rotation);

        Component[] front = new Component[4];
        Component[] back = new Component[4];
        for (int i = 0; i < LINES_PER_SIGN; i++) {
            Component line = i < batch.size() ? probeLine(batch.get(i)) : Component.empty();
            front[i] = line;
            back[i] = line;
        }

        Component control = Component.keybind(HackProbeClassifier.CONTROL_KEYBIND);
        front[3] = control;
        back[3] = control;

        SignText signText = new SignText(front, back, DyeColor.BLACK, false);
        SignBlockEntity sign = new SignBlockEntity(signPos, signState);
        sign.setLevel(world);
        sign.setText(signText, true);
        sign.setAllowedPlayerEditor(player.getUUID());

        int start = server.getTickCount();
        int timeout = Math.max(1, fileConfig.timeoutTicks);
        int deadline = start + timeout;
        int openAt = start + Math.max(1, fileConfig.openSignDelayTicks);

        run.waiting = new WaitingBatch(batch, signPos, sign, start, deadline, openAt);
    }

    private static Component probeLine(HackRegistryEntry entry) {
        String key = entry.key == null ? "" : entry.key;
        return switch (entry.mode) {
            case METEOR, TRANSLATE -> Component.translatableWithFallback(key, entry.fallback());
            case KEYBIND -> Component.keybind(key);
        };
    }

    private static void dispatchResult(
            ServerPlayer player,
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
                player.getUUID(),
                player.getName().getString(),
                hack.id,
                hack.key,
                hack.mode,
                state,
                w.startTick,
                w.deadlineTick,
                line,
                detail,
                w.signPos.immutable(),
                new String[0]
        );
        TranslationProbeHooks.dispatch(res);
    }

    private static void scheduleNextBatchOrFinish(MinecraftServer server, UUID playerId, CheckRun run) {
        if (run.batchIndex >= run.batches.size()) {
            finishCheck(server, playerId, run);
            return;
        }
        run.resumeAtTick = server.getTickCount() + Math.max(0, fileConfig.betweenSignTicks);
    }

    private static void finishCheck(MinecraftServer server, UUID playerId, CheckRun run) {
        RUNS.remove(playerId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        maybeRunAggregateCommands(server, player, run);
    }

    private static void maybeRunAggregateCommands(MinecraftServer server, ServerPlayer player, CheckRun run) {
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

    private static void runCommand(MinecraftServer server, String cmd, ServerPlayer player, String kind) {
        LOGGER.info("[BrokenStarSMP/CheckHacks] exec player={} kind={} cmd={}",
                player.getName().getString(), kind, cmd);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static void clearCurrentVirtualSign(MinecraftServer server, UUID playerId, CheckRun run) {
        WaitingBatch w = run.waiting;
        if (w == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            TranslationProbeSignHelper.clearVirtualSign(player, w.signPos);
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
        }
    }

    private static final class WaitingBatch {
        final List<HackRegistryEntry> batch;
        final BlockPos signPos;
        final SignBlockEntity sign;
        final int startTick;
        final int deadlineTick;
        final int openSignAtTick;

        WaitingBatch(List<HackRegistryEntry> batch, BlockPos signPos, SignBlockEntity sign, int startTick, int deadlineTick, int openSignAtTick) {
            this.batch = batch;
            this.signPos = signPos.immutable();
            this.sign = sign;
            this.startTick = startTick;
            this.deadlineTick = deadlineTick;
            this.openSignAtTick = openSignAtTick;
        }
    }
}
