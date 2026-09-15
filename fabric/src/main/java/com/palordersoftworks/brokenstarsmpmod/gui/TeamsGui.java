package com.palordersoftworks.brokenstarsmpmod.gui;

import com.palordersoftworks.brokenstarsmpmod.teams.TeamManager;
import com.palordersoftworks.brokenstarsmpmod.teams.TeamsCommand;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Chest GUI for team management: create/join via anvil text input, invites,
 * owner operations (kick/transfer/disband/color), and browsing open teams.
 *
 * <p>Wraps {@link TeamManager} so the GUI and {@link TeamsCommand} share the exact
 * same rules (ownership checks, invite requirements, member caps) and error strings.
 */
public final class TeamsGui {
    private TeamsGui() {}

    private static final int MAX_LISTED = 36;

    public static void open(ServerPlayer player) {
        openMain(player);
    }

    // ---- main ------------------------------------------------------------------

    private static void openMain(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(27), player, false);
        gui.setTitle(Component.literal("Teams"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        gui.setSlot(10, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(UiHelper.name("Create a Team", ChatFormatting.GOLD))
                .setLore(UiHelper.lore("Found a new team with you as owner.", "Team names: one word."))
                .setCallback((index, clickType, input, g) -> promptTeamName(player, "New team name:", name -> createTeam(player, name)))
                .build());

        gui.setSlot(11, new GuiElementBuilder(Items.NAME_TAG)
                .setName(UiHelper.name("Join a Team", ChatFormatting.AQUA))
                .setLore(UiHelper.lore("Browse open teams or type a team name."))
                .setCallback((index, clickType, input, g) -> openJoinBrowse(player))
                .build());

        gui.setSlot(12, new GuiElementBuilder(Items.BELL)
                .setName(UiHelper.name("Your Invites", ChatFormatting.YELLOW))
                .setLore(UiHelper.lore("Teams that invited you."))
                .setCallback((index, clickType, input, g) -> openInvites(player))
                .build());

        gui.setSlot(13, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(UiHelper.name("Your Team", ChatFormatting.GREEN))
                .setLore(UiHelper.lore("Leave, or manage if you own it."))
                .setCallback((index, clickType, input, g) -> openYourTeam(player))
                .build());

        gui.setSlot(15, new GuiElementBuilder(Items.SPYGLASS)
                .setName(UiHelper.name("Team List", ChatFormatting.WHITE))
                .setLore(UiHelper.lore("All teams on the server."))
                .setCallback((index, clickType, input, g) -> openAllTeams(player))
                .build());

        UiHelper.addCloseButton(gui);
        gui.open();
    }

    // ---- anvil text input ---------------------------------------------------------

    private static void promptTeamName(ServerPlayer player, String title, java.util.function.Consumer<String> onInput) {
        AnvilInputGui gui = new AnvilInputGui(player, false) {
            @Override
            public void onInput(String input) {
                this.close();
                onInput.accept(input.trim());
            }
        };
        gui.setTitle(Component.literal(title));
        gui.open();
    }

    private static void createTeam(ServerPlayer player, String name) {
        if (name.isEmpty()) {
            openMain(player);
            return;
        }
        String error = TeamManager.createTeam(name, player.getUUID());
        feedback(player, error, "Team " + name + " created! You are the owner.");
    }

    // ---- join / browse ---------------------------------------------------------------

    private static void openJoinBrowse(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(45), player, false);
        gui.setTitle(Component.literal("Join a Team"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        List<TeamManager.TeamData> teams = TeamManager.allTeams();
        int slot = 9;
        for (TeamManager.TeamData team : teams) {
            if (slot >= UiHelper.closeSlot(45) - 1) {
                break;
            }
            gui.setSlot(slot++, new GuiElementBuilder(Items.BANNER.white())
                    .setName(Component.literal(team.name()).withStyle(ChatFormatting.AQUA))
                    .setLore(UiHelper.lore(
                            team.members().size() + " member(s)",
                            "Owner: " + nameOf(player, team.owner()),
                            "Click to join"))
                    .setCallback((index, clickType, input, g) -> {
                        String error = TeamManager.join(team.name(), player.getUUID());
                        feedback(player, error, "You joined " + team.name() + "!");
                    })
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openMain(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    private static void openInvites(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(45), player, false);
        gui.setTitle(Component.literal("Your Invites"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        int slot = 9;
        for (String teamName : TeamManager.invitesFor(player.getUUID())) {
            if (slot >= UiHelper.closeSlot(45) - 1) {
                break;
            }
            gui.setSlot(slot++, new GuiElementBuilder(Items.BELL)
                    .setName(Component.literal(teamName).withStyle(ChatFormatting.YELLOW))
                    .setLore(UiHelper.lore("Click to accept"))
                    .setCallback((index, clickType, input, g) -> {
                        String error = TeamManager.join(teamName, player.getUUID());
                        feedback(player, error, "You joined " + teamName + "!");
                        openInvites(player);
                    })
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openMain(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    // ---- your team ----------------------------------------------------------------

    private static void openYourTeam(ServerPlayer player) {
        UUID uuid = player.getUUID();
        TeamManager.TeamData team = TeamManager.getTeamOf(uuid);
        if (team == null) {
            feedback(player, "You are not on a team.", null);
            openMain(player);
            return;
        }

        boolean owner = team.owner().equals(uuid);
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(27), player, false);
        gui.setTitle(Component.literal(team.name()));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        gui.setSlot(11, new GuiElementBuilder(Items.OAK_DOOR)
                .setName(UiHelper.name("Leave Team", ChatFormatting.RED))
                .setLore(UiHelper.lore("Leave " + team.name() + "."))
                .setCallback((index, clickType, input, g) -> {
                    String error = TeamManager.leave(uuid);
                    feedback(player, error, "You left " + team.name() + ".");
                    openMain(player);
                })
                .build());

        if (owner) {
            gui.setSlot(12, new GuiElementBuilder(Items.IRON_BOOTS)
                    .setName(UiHelper.name("Members", ChatFormatting.YELLOW))
                    .setLore(UiHelper.lore("Kick or transfer ownership."))
                    .setCallback((index, clickType, input, g) -> openMembers(player, team))
                    .build());

            gui.setSlot(13, new GuiElementBuilder(Items.DYE.lime())
                    .setName(UiHelper.name("Team Color", ChatFormatting.GREEN))
                    .setLore(UiHelper.lore("Nametag color."))
                    .setCallback((index, clickType, input, g) -> openColorPicker(player, team))
                    .build());

            gui.setSlot(15, new GuiElementBuilder(Items.TNT)
                    .setName(UiHelper.name("Disband Team", ChatFormatting.DARK_RED))
                    .setLore(UiHelper.lore("Deletes " + team.name() + " permanently."))
                    .setCallback((index, clickType, input, g) -> {
                        String error = TeamManager.deleteTeam(team.name(), uuid);
                        feedback(player, error, "Team " + team.name() + " disbanded.");
                        openMain(player);
                    })
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openMain(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    private static void openMembers(ServerPlayer player, TeamManager.TeamData team) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(45), player, false);
        gui.setTitle(Component.literal("Members: " + team.name()));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        int slot = 9;
        for (UUID member : team.members()) {
            if (member.equals(team.owner())) {
                continue;
            }
            if (slot >= UiHelper.closeSlot(45) - 1) {
                break;
            }
            String memberName = nameOf(player, member);
            gui.setSlot(slot++, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setName(Component.literal(memberName).withStyle(ChatFormatting.WHITE))
                    .setLore(UiHelper.lore(
                            "Left-click: kick",
                            "Right-click: transfer ownership"))
                    .setCallback((index, clickType, input, g) -> {
                        boolean right = clickType == eu.pb4.sgui.api.ClickType.MOUSE_RIGHT
                                || clickType == eu.pb4.sgui.api.ClickType.MOUSE_RIGHT_SHIFT;
                        String error = right
                                ? TeamManager.transfer(team.name(), member, player.getUUID())
                                : TeamManager.kick(team.name(), member, player.getUUID());
                        feedback(player, error, right
                                ? team.name() + " now belongs to " + memberName + "."
                                : memberName + " was removed from " + team.name() + ".");
                        openYourTeam(player);
                    })
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openYourTeam(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    private static void openColorPicker(ServerPlayer player, TeamManager.TeamData team) {
        String[][] colors = {
                {"red", "RED"}, {"gold", "GOLD"}, {"yellow", "YELLOW"}, {"green", "GREEN"},
                {"aqua", "AQUA"}, {"blue", "BLUE"}, {"light_purple", "LIGHT_PURPLE"}, {"white", "WHITE"},
                {"black", "BLACK"}, {"dark_blue", "DARK_BLUE"}, {"dark_green", "DARK_GREEN"},
                {"dark_aqua", "DARK_AQUA"}, {"dark_red", "DARK_RED"}, {"dark_purple", "DARK_PURPLE"},
                {"gray", "GRAY"}, {"dark_gray", "DARK_GRAY"},
        };
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(45), player, false);
        gui.setTitle(Component.literal("Color: " + team.name()));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        int slot = 9;
        for (String[] color : colors) {
            if (slot >= UiHelper.closeSlot(45) - 1) {
                break;
            }
            ChatFormatting formatting = ChatFormatting.valueOf(color[1]);
            gui.setSlot(slot++, new GuiElementBuilder(Items.DYE.red())
                    .setName(Component.literal(color[0]).withStyle(formatting))
                    .setLore(UiHelper.lore("Click to apply"))
                    .setCallback((index, clickType, input, g) -> {
                        String error = TeamManager.setColor(team.name(), color[0], player.getUUID());
                        feedback(player, error, "Team color updated to " + color[0] + ".");
                        openYourTeam(player);
                    })
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openYourTeam(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    // ---- list ---------------------------------------------------------------------

    private static void openAllTeams(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(UiHelper.menuTypeFor(45), player, false);
        gui.setTitle(Component.literal("All Teams"));
        UiHelper.fill(gui, Items.STAINED_GLASS_PANE.gray());

        List<TeamManager.TeamData> teams = TeamManager.allTeams();
        int slot = 0;
        for (TeamManager.TeamData team : teams) {
            if (slot >= MAX_LISTED) {
                break;
            }
            gui.setSlot(slot++, new GuiElementBuilder(Items.BANNER.white())
                    .setName(Component.literal(team.name()).withStyle(ChatFormatting.AQUA))
                    .setLore(UiHelper.lore(
                            team.members().size() + " member(s)",
                            "Owner: " + nameOf(player, team.owner()),
                            "Color: " + team.color()))
                    .build());
        }

        UiHelper.addBackButton(gui, () -> openMain(player));
        UiHelper.addCloseButton(gui);
        gui.open();
    }

    // ---- helpers ----------------------------------------------------------------------

    private static String nameOf(ServerPlayer viewer, UUID target) {
        ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(target);
        if (online != null) {
            return online.getName().getString();
        }
        return viewer.level().getServer().services().nameToIdCache().get(target)
                .map(entry -> entry.name())
                .orElse("unknown");
    }

    private static void feedback(ServerPlayer player, String error, String success) {
        if (error != null) {
            player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        } else if (success != null) {
            player.sendSystemMessage(Component.literal(success).withStyle(ChatFormatting.GREEN));
        }
    }
}
