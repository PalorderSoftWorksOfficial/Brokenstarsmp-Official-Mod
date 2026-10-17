package com.palordersoftworks.brokenstarsmpmod.framework;

import com.palordersoftworks.brokenstarsmpmod.framework.events.EventDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolution;
import com.palordersoftworks.brokenstarsmpmod.gui.UiHelper;
import com.palordersoftworks.brokenstarsmpmod.helpers.PermissionCompat;
import com.palordersoftworks.brokenstarsmpmod.permissions.PermissionNodes;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AdminControlPanel {
    private static final int PAGE_SIZE = 45;

    private AdminControlPanel() {
    }

    public static boolean open(ServerPlayer player) {
        if (!PermissionCompat.hasAny(player.createCommandSourceStack(), PermissionNodes.GAMEMASTER,
                PermissionNodes.ADMINISTRATOR, PermissionNodes.CONFIG_MANAGER, PermissionNodes.MECHANIC_MANAGER)) {
            player.sendSystemMessage(Component.literal("You do not have access to the admin panel.").withStyle(ChatFormatting.RED));
            return false;
        }
        open(player, 0, "", "home");
        return true;
    }

    public static boolean search(ServerPlayer player, String query) {
        if (!PermissionCompat.hasAny(player.createCommandSourceStack(), PermissionNodes.GAMEMASTER,
                PermissionNodes.ADMINISTRATOR, PermissionNodes.CONFIG_MANAGER, PermissionNodes.MECHANIC_MANAGER)) {
            return false;
        }
        open(player, 0, query == null ? "" : query, "mechanics");
        return true;
    }

    private static void open(ServerPlayer player, int page, String query, String category) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(54), player, false);
        gui.setTitle(Component.literal("BrokenStars Admin"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());
        if ("home".equals(category)) {
            renderHome(gui, player);
        } else if ("mechanics".equals(category)) {
            renderMechanics(gui, player, page, query);
        } else if ("events".equals(category)) {
            renderEvents(gui, player);
        } else if ("profiles".equals(category)) {
            renderProfiles(gui, player);
        } else {
            renderDiagnostics(gui, player);
        }
        gui.open();
    }

    private static void renderHome(SimpleGui gui, ServerPlayer player) {
        gui.setSlot(10, categoryButton(Items.COMPARATOR, "Mechanics", "Browse and toggle live rules",
                () -> open(player, 0, "", "mechanics")));
        gui.setSlot(12, categoryButton(Items.CLOCK, "Events", "Inspect and control temporary events",
                () -> open(player, 0, "", "events")));
        gui.setSlot(14, categoryButton(Items.BOOK, "Profiles", "Apply named mechanic profiles",
                () -> open(player, 0, "", "profiles")));
        gui.setSlot(16, categoryButton(Items.COMPARATOR, "Diagnostics", "Performance and configuration status",
                () -> open(player, 0, "", "diagnostics")));
        gui.setSlot(40, new GuiElementBuilder(Items.PAPER)
                .setName(UiHelper.name("Configuration status", ChatFormatting.YELLOW))
                .setCallback((index, clickType, input, current) -> {
                    for (String line : BrokenStarsFramework.get().statusLines()) {
                        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.AQUA));
                    }
                })
                .build());
        UiHelper.addCloseButton(gui);
    }

    private static void renderMechanics(SimpleGui gui, ServerPlayer player, int page, String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<MechanicDefinition> definitions = BrokenStarsFramework.get().registry().definitions().values().stream()
                .filter(definition -> normalized.isEmpty() || definition.id().contains(normalized))
                .sorted(Comparator.comparing(MechanicDefinition::id))
                .toList();
        int pages = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        int start = currentPage * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < definitions.size(); index++) {
            MechanicDefinition definition = definitions.get(start + index);
            MechanicResolution resolution = BrokenStarsFramework.get().read(definition.id(), BrokenStarsFramework.get().context(player));
            ItemStackData data = ItemStackData.forDefinition(definition);
            gui.setSlot(index, new GuiElementBuilder(data.item())
                    .setName(UiHelper.name(definition.id(), ChatFormatting.YELLOW))
                    .setLore(List.of(
                            UiHelper.name("Effective: " + String.valueOf(resolution.value()), ChatFormatting.GREEN),
                            UiHelper.name("Default: " + String.valueOf(resolution.defaultValue()), ChatFormatting.GRAY),
                            UiHelper.name("Source: " + resolution.source(), ChatFormatting.AQUA),
                            UiHelper.name("Scope: " + resolution.scope(), ChatFormatting.GRAY),
                            UiHelper.name("Restart required: " + resolution.restartRequired(), ChatFormatting.RED),
                            UiHelper.name(definition.description(), ChatFormatting.DARK_GRAY)))
                    .setCallback((index1, clickType, input, current) -> {
                        if (definition.type() == com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicValueType.BOOLEAN) {
                            boolean next = !(resolution.value() instanceof Boolean value) || !value;
                            BrokenStarsFramework.get().write(definition.id(), next, BrokenStarsFramework.get().context(player),
                                    player.getUUID(), player.getName().getString());
                            open(player, currentPage, query, categoryForMechanic(definition.id()));
                        }
                    })
                    .build());
        }
        addPagination(gui, player, currentPage, pages, query, "mechanics");
        addBackHome(gui, player);
        UiHelper.addCloseButton(gui);
    }

    private static void renderEvents(SimpleGui gui, ServerPlayer player) {
        List<EventDefinition> definitions = BrokenStarsFramework.get().events().all();
        for (int index = 0; index < Math.min(definitions.size(), PAGE_SIZE); index++) {
            EventDefinition definition = definitions.get(index);
            boolean active = BrokenStarsFramework.get().events().active().stream().anyMatch(value -> value.id().equals(definition.id()));
            gui.setSlot(index, new GuiElementBuilder(Items.REDSTONE)
                    .setName(UiHelper.name(definition.id(), active ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .setLore(List.of(
                            UiHelper.name("Enabled in config: " + definition.enabled(), ChatFormatting.GRAY),
                            UiHelper.name("Duration: " + definition.durationTicks() + " ticks", ChatFormatting.GRAY),
                            UiHelper.name("Priority: " + definition.priority(), ChatFormatting.GRAY),
                            UiHelper.name(active ? "Click to stop" : "Click to start", ChatFormatting.AQUA)))
                    .setCallback((index1, clickType, input, current) -> {
                        if (active) {
                            BrokenStarsFramework.get().events().stop(definition.id());
                        } else {
                            BrokenStarsFramework.get().events().start(definition.id());
                        }
                        open(player, 0, "", "events");
                    })
                    .build());
        }
        addBackHome(gui, player);
        UiHelper.addCloseButton(gui);
    }

    private static void renderProfiles(SimpleGui gui, ServerPlayer player) {
        List<com.palordersoftworks.brokenstarsmpmod.framework.profiles.MechanicProfile> profiles = BrokenStarsFramework.get().profiles().all();
        for (int index = 0; index < Math.min(profiles.size(), PAGE_SIZE); index++) {
            var profile = profiles.get(index);
            boolean active = profile.id().equals(BrokenStarsFramework.get().profiles().active());
            gui.setSlot(index, new GuiElementBuilder(Items.BOOK)
                    .setName(UiHelper.name(profile.id(), active ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .setLore(List.of(
                            UiHelper.name("Overrides: " + profile.overrides().size(), ChatFormatting.GRAY),
                            UiHelper.name("Parent: " + (profile.extendsProfile() == null ? "none" : profile.extendsProfile()), ChatFormatting.GRAY),
                            UiHelper.name(active ? "Active" : "Click to apply", ChatFormatting.AQUA)))
                    .setCallback((index1, clickType, input, current) -> {
                        BrokenStarsFramework.get().profiles().apply(profile.id());
                        open(player, 0, "", "profiles");
                    })
                    .build());
        }
        addBackHome(gui, player);
        UiHelper.addCloseButton(gui);
    }

    private static void renderDiagnostics(SimpleGui gui, ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.addAll(BrokenStarsFramework.get().performance().diagnostics());
        lines.addAll(BrokenStarsFramework.get().statusLines());
        for (int index = 0; index < Math.min(lines.size(), PAGE_SIZE); index++) {
            gui.setSlot(index, new GuiElementBuilder(Items.PAPER)
                    .setName(UiHelper.name(lines.get(index), ChatFormatting.AQUA))
                    .build());
        }
        addBackHome(gui, player);
        UiHelper.addCloseButton(gui);
    }

    private static void addPagination(SimpleGui gui, ServerPlayer player, int page, int pages, String query, String category) {
        if (page > 0) {
            gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(UiHelper.name("Previous page", ChatFormatting.YELLOW))
                    .setCallback((index, clickType, input, current) -> open(player, page - 1, query, category))
                    .build());
        }
        gui.setSlot(49, new GuiElementBuilder(Items.PAPER)
                .setName(UiHelper.name("Page " + (page + 1) + "/" + pages, ChatFormatting.GRAY))
                .build());
        if (page + 1 < pages) {
            gui.setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(UiHelper.name("Next page", ChatFormatting.YELLOW))
                    .setCallback((index, clickType, input, current) -> open(player, page + 1, query, category))
                    .build());
        }
    }

    private static void addBackHome(SimpleGui gui, ServerPlayer player) {
        gui.setSlot(48, new GuiElementBuilder(Items.ARROW)
                .setName(UiHelper.name("Back", ChatFormatting.RED))
                .setCallback((index, clickType, input, current) -> open(player, 0, "", "home"))
                .build());
    }

    private static SimpleGuiElement categoryButton(net.minecraft.world.item.Item item, String name, String description, Runnable action) {
        return new GuiElementBuilder(item)
                .setName(UiHelper.name(name, ChatFormatting.GOLD))
                .setLore(List.of(UiHelper.name(description, ChatFormatting.GRAY)))
                .setCallback((index, clickType, input, current) -> action.run())
                .build();
    }

    private static String categoryForMechanic(String id) {
        return id.startsWith("event") ? "events" : id.startsWith("profile") ? "profiles" : "mechanics";
    }

    private record ItemStackData(net.minecraft.world.item.Item item) {
        static ItemStackData forDefinition(MechanicDefinition definition) {
            net.minecraft.world.item.Item item = switch (definition.type()) {
                case BOOLEAN -> Items.LEVER;
                case INTEGER, DURATION -> Items.COMPARATOR;
                case NUMBER, MULTIPLIER, PROBABILITY -> Items.DIAMOND;
                case LIST, SET -> Items.BUNDLE;
                case MAP, NESTED, PREDICATE -> Items.WRITABLE_BOOK;
                default -> Items.PAPER;
            };
            return new ItemStackData(item);
        }
    }
}
