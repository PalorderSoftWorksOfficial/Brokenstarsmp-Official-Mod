package com.palordersoftworks.brokenstarsmpmod.config;

import com.palordersoftworks.brokenstarsmpmod.config.Rule;

public class ServerRules {

    @Rule(desc = "Prevents anvils from taking damage when landing",name = "PreventAnvilDamage")
    public static boolean PREVENT_ANVIL_DAMAGE = true;

    @Rule(desc = "Whether filled shulker boxes should stack",name = "FilledShulkerBoxesStack")
    public static boolean FILLED_SHULKERS_STACK = true;

    @Rule(desc = "Furnace cooking speed in ticks per item", strict = true,name = "FurnaceCookingSpeed")
    public static int FURNACE_COOKING_SPEED = 200;
    @Rule(desc = "Modifies what the radius is for items to be dropped at your feet", strict = true,name = "DropAtFeetRadius")
    public static int DROP_AT_FEET_RADIUS = 3;
    @Rule(desc = "Maximum range at which experience orbs move towards players",name = "ExperienceOrbRange")
    public static double EXPERIENCE_ORB_RANGE = 64.0;

    @Rule(desc = "Honey level increment per server tick", strict = true,name = "BeehiveHoneyIncrement")
    public static int BEEHIVE_HONEY_INCREMENT = 1;

    @Rule(desc = "Allow chests to be opened even if blocked", strict = true,name = "AllowChestOpeningUnderBlocks")
    public static boolean ALLOW_CHEST_OPENING = true;

    @Rule(desc = "Whether Redstone Wire Optimizations are active",name = "fastRedstoneDust")
    public static boolean REDSTONE_WIRE_TURBO = false;
    @Rule(
            desc = "Amount of items a dispenser drops per activation (0 = 1 item)",
            strict = true,
            name = "dispenserDropAmount"
    )
    public static int DISPENSER_DROP_AMOUNT = 0;
    @Rule(desc = "§kNull null null null",name = "§k§cnull§r")
    public static boolean NULL_ENABLED = false;
}