package com.palordersoftworks.brokenstarsmpmod.config;

public class UnstableSMPRules {

    @Rule(
            desc = "§7§oEnables the /immortal command and immortality tracking.",
            name = "immortalSystemEnabled"
    )
    public static boolean IMMORTAL_SYSTEM_ENABLED = true;

    @Rule(
            desc = "§7§oAllows immortal players to be disabled by holding a Totem of Undying.",
            name = "smartTotemDetection"
    )
    public static boolean SMART_TOTEM_DETECTION = true;

    @Rule(
            desc = "§7§oEnables death bans when a player dies.",
            name = "deathBanEnabled"
    )
    public static boolean DEATH_BAN_ENABLED = true;

    @Rule(
            desc = "§7§oReason used when a player is banned after death.",
            name = "deathBanReason"
    )
    public static String DEATH_BAN_REASON = "You Died with a Hardcore Plugin";

    @Rule(
            desc = "§7§oPlays a Wither spawn sound when a player dies.",
            name = "witherSoundEnabled"
    )
    public static boolean WITHER_SOUND_ENABLED = true;

    @Rule(
            desc = "§7§oDistance in blocks for the Wither death sound.",
            name = "witherSoundDistance",
            strict = true
    )
    public static int WITHER_SOUND_DISTANCE = 500;

    @Rule(
            desc = "§7§oMakes join, leave, and death messages local instead of global.",
            name = "proximityMessagesEnabled"
    )
    public static boolean PROXIMITY_MESSAGES_ENABLED = true;

    @Rule(
            desc = "§7§oDistance in blocks for proximity messages.",
            name = "proximityMessagesDistance",
            strict = true
    )
    public static int PROXIMITY_MESSAGES_DISTANCE = 500;
}
