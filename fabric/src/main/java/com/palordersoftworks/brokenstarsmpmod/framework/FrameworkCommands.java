package com.palordersoftworks.brokenstarsmpmod.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.palordersoftworks.brokenstarsmpmod.framework.config.ConfigIssue;
import com.palordersoftworks.brokenstarsmpmod.framework.config.ReloadResult;
import com.palordersoftworks.brokenstarsmpmod.framework.emergency.EmergencyService;
import com.palordersoftworks.brokenstarsmpmod.framework.events.EventDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.events.EventService;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolution;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.palordersoftworks.brokenstarsmpmod.permissions.PermissionNodes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FrameworkCommands {
    private FrameworkCommands() {
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("reload")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.CONFIG_MANAGER,
                        PermissionNodes.MECHANIC_MANAGER, PermissionNodes.GAMEMASTER))
                .executes(context -> reload(context.getSource(), null))
                .then(sectionNode("all", context -> reload(context.getSource(), "all")))
                .then(sectionNode("server rules", context -> reload(context.getSource(), "server rules")))
                .then(sectionNode("mechanics", context -> reload(context.getSource(), "mechanics")))
                .then(sectionNode("entities", context -> reload(context.getSource(), "entities")))
                .then(sectionNode("items", context -> reload(context.getSource(), "items")))
                .then(sectionNode("blocks", context -> reload(context.getSource(), "blocks")))
                .then(sectionNode("events", context -> reload(context.getSource(), "events")))
                .then(sectionNode("profiles", context -> reload(context.getSource(), "profiles")))
                .then(sectionNode("messages", context -> reload(context.getSource(), "messages")))
                .then(sectionNode("scripts", context -> reload(context.getSource(), "scripts"))));

        root.then(Commands.literal("config")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.CONFIG_MANAGER,
                        PermissionNodes.MECHANIC_MANAGER, PermissionNodes.GAMEMASTER))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("issues").executes(context -> issues(context.getSource()))));

        root.then(Commands.literal("mechanic")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.MECHANIC_MANAGER,
                        PermissionNodes.GAMEMASTER))
                .then(Commands.literal("list").executes(context -> mechanicList(context.getSource())))
                .then(Commands.literal("get")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> mechanicGet(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> mechanicGet(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> mechanicSet(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "value"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> mechanicRemove(context.getSource(), StringArgumentType.getString(context, "id"))))));

        root.then(Commands.literal("profile")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.MECHANIC_MANAGER,
                        PermissionNodes.GAMEMASTER))
                .then(Commands.literal("list").executes(context -> profileList(context.getSource())))
                .then(Commands.literal("active").executes(context -> profileActive(context.getSource())))
                .then(Commands.literal("apply")
                        .then(Commands.argument("profile", StringArgumentType.word())
                                .executes(context -> profileApply(context.getSource(), StringArgumentType.getString(context, "profile")))))
                .then(Commands.literal("create")
                        .then(Commands.argument("profile", StringArgumentType.word())
                                .executes(context -> profileCreate(context.getSource(), StringArgumentType.getString(context, "profile"), null))
                                .then(Commands.argument("parent", StringArgumentType.word())
                                        .executes(context -> profileCreate(context.getSource(), StringArgumentType.getString(context, "profile"), StringArgumentType.getString(context, "parent"))))))
                .then(Commands.literal("reload").executes(context -> {
                    BrokenStarsFramework framework = BrokenStarsFramework.get();
                    framework.reload("profiles", actor(context.getSource()), actorName(context.getSource()));
                    send(context.getSource(), Component.literal("Profiles reloaded.").withStyle(ChatFormatting.GREEN));
                    return 1;
                })));

        root.then(Commands.literal("emergency")
                .requires(source -> PermissionCompat.has(source, PermissionNodes.EMERGENCY))
                .then(Commands.literal("status").executes(context -> emergencyStatus(context.getSource())))
                .then(Commands.literal("restore").executes(context -> emergencyRestore(context.getSource())))
                .then(Commands.literal("mechanics")
                        .then(Commands.literal("safe").executes(context -> emergencySet(context.getSource(), EmergencyService.Toggle.MECHANICS, true, -1))))
                .then(Commands.argument("toggle", StringArgumentType.word())
                        .then(Commands.argument("state", BoolArgumentType.bool())
                                .executes(context -> emergencySet(context.getSource(), parseToggle(StringArgumentType.getString(context, "toggle")),
                                        BoolArgumentType.getBool(context, "state"), -1))
                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                        .executes(context -> emergencySet(context.getSource(), parseToggle(StringArgumentType.getString(context, "toggle")),
                                                BoolArgumentType.getBool(context, "state"), IntegerArgumentType.getInteger(context, "duration")))))));

        root.then(Commands.literal("performance")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.PERFORMANCE_MANAGER,
                        PermissionNodes.GAMEMASTER))
                .executes(context -> performance(context.getSource())));

        root.then(Commands.literal("audit")
                .requires(source -> PermissionCompat.has(source, PermissionNodes.AUDIT))
                .executes(context -> audit(context.getSource())));

        root.then(Commands.literal("admin")
                .requires(source -> PermissionCompat.hasAny(source, PermissionNodes.GAMEMASTER,
                        PermissionNodes.ADMINISTRATOR, PermissionNodes.CONFIG_MANAGER, PermissionNodes.MECHANIC_MANAGER))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) {
                        fail(context.getSource(), "Players only");
                        return 0;
                    }
                    return AdminControlPanel.open(player) ? 1 : 0;
                })
                .then(Commands.literal("search")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) {
                                        fail(context.getSource(), "Players only");
                                        return 0;
                                    }
                                    return AdminControlPanel.search(player, StringArgumentType.getString(context, "query")) ? 1 : 0;
                                }))));
    }

    public static void registerAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bsmpevent")
                .requires(source -> PermissionCompat.has(source, PermissionNodes.EVENT_MANAGER))
                .then(Commands.literal("list").executes(context -> eventList(context.getSource())))
                .then(Commands.literal("active").executes(context -> eventActive(context.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(context -> eventStart(context.getSource(), StringArgumentType.getString(context, "event")))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(context -> eventStop(context.getSource(), StringArgumentType.getString(context, "event"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sectionNode(String name, com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.literal(name).executes(command);
    }

    private static int reload(CommandSourceStack source, String section) {
        BrokenStarsFramework framework = BrokenStarsFramework.get();
        ReloadResult result = framework.reload(section, actor(source), actorName(source));
        send(source, Component.literal("Reload " + (section == null ? "all" : section) + ": "
                + (result.successful() ? "completed" : "completed with validation errors")
                + ", rules=" + result.loadedRules() + ", issues=" + result.issues().size()).withStyle(result.successful() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return result.successful() ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        for (String line : BrokenStarsFramework.get().statusLines()) {
            send(source, Component.literal(line).withStyle(ChatFormatting.AQUA));
        }
        return 1;
    }

    private static int issues(CommandSourceStack source) {
        List<ConfigIssue> issues = BrokenStarsFramework.get().config().issues();
        if (issues.isEmpty()) {
            send(source, Component.literal("No configuration issues.").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        for (ConfigIssue issue : issues.stream().limit(50).toList()) {
            send(source, Component.literal(issue.file() + " " + issue.section() + "." + issue.key()
                    + " value=" + issue.value() + " expected=" + issue.expected() + " fallback=" + issue.fallback()
                    + " message=" + issue.message()).withStyle(issue.severity() == ConfigIssue.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int mechanicList(CommandSourceStack source) {
        BrokenStarsFramework framework = BrokenStarsFramework.get();
        for (MechanicDefinition definition : framework.registry().definitions().values()) {
            send(source, Component.literal(definition.id() + " = " + String.valueOf(definition.defaultValue())
                    + (definition.restartRequired() ? " [restart]" : "")).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int mechanicGet(CommandSourceStack source, String id) {
        try {
            MechanicResolution resolution = BrokenStarsFramework.get().read(id, context(source));
            send(source, Component.literal(id + "=" + String.valueOf(resolution.value()) + " source=" + resolution.source()
                    + " scope=" + resolution.scope() + " restart=" + resolution.restartRequired()).withStyle(ChatFormatting.AQUA));
            return 1;
        } catch (RuntimeException exception) {
            fail(source, exception.getMessage());
            return 0;
        }
    }

    private static int mechanicSet(CommandSourceStack source, String id, String raw) {
        try {
            MechanicDefinition definition = BrokenStarsFramework.get().registry().require(id);
            Object value = parseCommandValue(definition, raw);
            MechanicResolution result = BrokenStarsFramework.get().write(id, value, context(source), actor(source), actorName(source));
            send(source, Component.literal(id + " set to " + String.valueOf(result.value()) + " for this context.").withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (RuntimeException exception) {
            fail(source, exception.getMessage());
            return 0;
        }
    }

    private static int mechanicRemove(CommandSourceStack source, String id) {
        boolean removed = BrokenStarsFramework.get().remove(id, actor(source), actorName(source));
        send(source, Component.literal(removed ? "Removed override " + id : "No override " + id).withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return removed ? 1 : 0;
    }

    private static int profileList(CommandSourceStack source) {
        for (com.palordersoftworks.brokenstarsmpmod.framework.profiles.MechanicProfile profile : BrokenStarsFramework.get().profiles().all()) {
            send(source, Component.literal(profile.id() + " extends=" + profile.extendsProfile() + " overrides=" + profile.overrides().size()).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int profileActive(CommandSourceStack source) {
        send(source, Component.literal(BrokenStarsFramework.get().profiles().active()).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int profileApply(CommandSourceStack source, String profile) {
        boolean applied = BrokenStarsFramework.get().profiles().apply(profile);
        BrokenStarsFramework.get().recordAudit(actor(source), actorName(source), "profile.apply", profile, "inactive", "active", "command");
        send(source, Component.literal(applied ? "Applied profile " + profile : "Unknown profile " + profile).withStyle(applied ? ChatFormatting.GREEN : ChatFormatting.RED));
        return applied ? 1 : 0;
    }

    private static int profileCreate(CommandSourceStack source, String profile, String parent) {
        boolean created = BrokenStarsFramework.get().profiles().create(profile, parent);
        send(source, Component.literal(created ? "Created profile " + profile : "Profile could not be created").withStyle(created ? ChatFormatting.GREEN : ChatFormatting.RED));
        return created ? 1 : 0;
    }

    private static int emergencySet(CommandSourceStack source, EmergencyService.Toggle toggle, boolean enabled, int duration) {
        if (toggle == null) {
            fail(source, "Unknown emergency toggle");
            return 0;
        }
        BrokenStarsFramework.get().emergency().set(toggle, enabled, duration, source.getServer());
        BrokenStarsFramework.get().recordAudit(actor(source), actorName(source), "emergency.set", toggle.name(), !enabled, enabled, "command");
        send(source, Component.literal(toggle.name().toLowerCase(Locale.ROOT) + " " + (enabled ? "enabled" : "disabled")).withStyle(enabled ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        return 1;
    }

    private static int emergencyStatus(CommandSourceStack source) {
        send(source, Component.literal(String.join(", ", BrokenStarsFramework.get().emergency().status(source.getServer()))).withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int emergencyRestore(CommandSourceStack source) {
        BrokenStarsFramework.get().emergency().clear();
        BrokenStarsFramework.get().recordAudit(actor(source), actorName(source), "emergency.restore", "all", "emergency", "normal", "command");
        send(source, Component.literal("Emergency overrides restored.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int performance(CommandSourceStack source) {
        for (String line : BrokenStarsFramework.get().performance().diagnostics()) {
            send(source, Component.literal(line).withStyle(ChatFormatting.AQUA));
        }
        return 1;
    }

    private static int audit(CommandSourceStack source) {
        BrokenStarsFramework framework = BrokenStarsFramework.get();
        var current = framework.config().directory().resolve("data").resolve("audit.log");
        try {
            List<String> lines = java.nio.file.Files.exists(current) ? java.nio.file.Files.readAllLines(current) : List.of();
            for (String line : lines.stream().skip(Math.max(0, lines.size() - 20)).toList()) {
                send(source, Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        } catch (java.io.IOException exception) {
            fail(source, "Unable to read audit log");
        }
        return 1;
    }

    private static int eventList(CommandSourceStack source) {
        for (EventDefinition definition : BrokenStarsFramework.get().events().all()) {
            send(source, Component.literal(definition.id() + " enabled=" + definition.enabled() + " duration=" + definition.durationTicks()).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int eventActive(CommandSourceStack source) {
        for (EventDefinition definition : BrokenStarsFramework.get().events().active()) {
            send(source, Component.literal(definition.id()).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int eventStart(CommandSourceStack source, String id) {
        EventService.StartResult result = BrokenStarsFramework.get().events().start(id);
        BrokenStarsFramework.get().recordAudit(actor(source), actorName(source), "event.start", id, "inactive", result.successful() ? "active" : result.message(), "command");
        send(source, Component.literal(result.successful() ? "Started event " + id : result.message()).withStyle(result.successful() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.successful() ? 1 : 0;
    }

    private static int eventStop(CommandSourceStack source, String id) {
        boolean stopped = BrokenStarsFramework.get().events().stop(id);
        BrokenStarsFramework.get().recordAudit(actor(source), actorName(source), "event.stop", id, "active", stopped ? "inactive" : "not-active", "command");
        send(source, Component.literal(stopped ? "Stopped event " + id : "Event is not active").withStyle(stopped ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return stopped ? 1 : 0;
    }

    private static EmergencyService.Toggle parseToggle(String value) {
        try {
            return EmergencyService.Toggle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Object parseCommandValue(MechanicDefinition definition, String raw) {
        return switch (definition.type()) {
            case BOOLEAN -> Boolean.parseBoolean(raw);
            case INTEGER, DURATION -> Long.parseLong(raw);
            case NUMBER, MULTIPLIER, PROBABILITY -> Double.parseDouble(raw);
            default -> raw;
        };
    }

    private static MechanicContext context(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? MechanicContext.global() : BrokenStarsFramework.get().context(player);
    }

    private static UUID actor(CommandSourceStack source) {
        return source.getPlayer() == null ? null : source.getPlayer().getUUID();
    }

    private static String actorName(CommandSourceStack source) {
        return source.getPlayer() == null ? "console" : source.getPlayer().getName().getString();
    }

    private static void send(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    private static void fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message == null ? "Invalid request" : message).withStyle(ChatFormatting.RED));
    }
}
