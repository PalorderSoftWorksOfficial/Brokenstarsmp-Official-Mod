package com.palordersoftworks.brokenstarsmpmod.config;

import com.palordersoftworks.brokenstarsmpmod.config.Rule;

public class ServerRules {

    @Rule(desc = "Prevents anvils from taking damage when landing")
    public static boolean PREVENT_ANVIL_DAMAGE = true;

    @Rule(desc = "Whether filled shulker boxes should stack")
    public static boolean FILLED_SHULKERS_STACK = true;

    @Rule(desc = "Furnace cooking speed in ticks per item", strict = true)
    public static int FURNACE_COOKING_SPEED = 200;
    @Rule(desc = "Furnace cooking speed in ticks per item", strict = true)
    public static int DROP_AT_FEET_RADIUS = 3;
    @Rule(desc = "Maximum range at which experience orbs move towards players")
    public static double EXPERIENCE_ORB_RANGE = 64.0;

    @Rule(desc = "Honey level increment per server tick", strict = true)
    public static int BEEHIVE_HONEY_INCREMENT = 1;

    @Rule(desc = "Allow chests to be opened even if blocked", strict = true)
    public static boolean ALLOW_CHEST_OPENING = true;

    @Rule(desc = "Whether Redstone Wire Optimizations are active")
    public static boolean REDSTONE_WIRE_TURBO = true;
}