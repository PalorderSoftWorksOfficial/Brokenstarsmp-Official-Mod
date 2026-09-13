package com.palordersoftworks.brokenstarsmpmod.config;

public class TeamRules {

    @Rule(
            desc = "§7§oEnables the /teams command and team features.",
            name = "teamsEnabled"
    )
    public static boolean TEAMS_ENABLED = true;

    @Rule(
            desc = "§7§oRequires an invite from the team owner to join a team.",
            name = "teamRequireInvite"
    )
    public static boolean TEAM_REQUIRE_INVITE = true;

    @Rule(
            desc = "§7§oMaximum members per team. §8(0 = unlimited)",
            name = "teamMaxMembers",
            strict = true
    )
    public static int TEAM_MAX_MEMBERS = 0;

    @Rule(
            desc = "§7§oPrevents teammates from hurting each other with melee attacks.",
            name = "teamFriendlyFire",
            strict = true
    )
    public static boolean TEAM_FRIENDLY_FIRE = false;

    @Rule(
            desc = "§7§oPrevents projectile damage (arrows, tridents) between teammates.",
            name = "teamProjectileFriendlyFire",
            strict = true
    )
    public static boolean TEAM_PROJECTILE_FRIENDLY_FIRE = false;

    @Rule(
            desc = "§7§oDisables teammate vs teammate PvP entirely. §8(Overrides the rules above)",
            name = "teamNoPvp",
            strict = true
    )
    public static boolean TEAM_NO_PVP = false;
}
