package com.palordersoftworks.brokenstarsmpmod.config;

public class ServerRules {

    @Rule(
            desc = "§7§oPrevents anvils from taking damage when landing.",
            name = "preventAnvilDamage"
    )
    public static boolean PREVENT_ANVIL_DAMAGE = true;

    @Rule(
            desc = "§7§oAllows filled shulker boxes to stack.",
            name = "filledShulkerBoxesStack"
    )
    public static boolean FILLED_SHULKERS_STACK = true;

    @Rule(
            desc = "§7§oFurnace cooking speed in ticks per item.",
            name = "furnaceCookingSpeed",
            strict = true
    )
    public static int FURNACE_COOKING_SPEED = 200;

    @Rule(
            desc = "§7§oModifies the radius at which items are dropped at your feet.",
            name = "dropAtFeetRadius",
            strict = true
    )
    public static int DROP_AT_FEET_RADIUS = 0;

    @Rule(
            desc = "§7§oMaximum range at which experience orbs move toward players.",
            name = "experienceOrbRange"
    )
    public static double EXPERIENCE_ORB_RANGE = 64.0;

    @Rule(
            desc = "§7§oHoney level increment per server tick.",
            name = "beehiveHoneyIncrement",
            strict = true
    )
    public static int BEEHIVE_HONEY_INCREMENT = 1;

    @Rule(
            desc = "§7§oAllows chests to be opened even if blocked.",
            name = "allowChestOpeningUnderBlocks",
            strict = true
    )
    public static boolean ALLOW_CHEST_OPENING = true;

    @Rule(
            desc = "§7§oAmount of items a dispenser drops per activation. §8(0 = 1 item)",
            name = "dispenserDropAmount",
            strict = true
    )
    public static int DISPENSER_DROP_AMOUNT = 0;

    @Rule(
            desc = "§c§oNull null null null.",
            name = "nullEnabled"
    )
    public static boolean NULL_ENABLED = false;

    @Rule(
            desc = "§7§oNumber of items consumed per crafting operation in the Crafter block.",
            name = "crafterCraftAmount"
    )
    public static int CRAFTER_CRAFT_AMOUNT = 0;

    @Rule(
            desc = "§7§oReplaces cobblestone from lava/water interaction with a random ore.",
            name = "randomOreCobblestone"
    )
    public static boolean RANDOM_ORE_COBBLESTONE = false;

    @Rule(
            desc = "§7§oItem entities despawn after this many ticks. §8(-1 = vanilla)",
            name = "itemDespawnTicks",
            strict = true
    )
    public static int ITEM_DESPAWN_TICKS = -1;

    @Rule(
            desc = "§7§oMaximum number of entities processed per world tick. §8(-1 = vanilla)",
            name = "entityTickBudget",
            strict = true
    )
    public static int ENTITY_TICK_BUDGET = -1;

    @Rule(
            desc = "§7§oMaximum number of block entities ticked per world tick. §8(-1 = vanilla)",
            name = "blockEntityTickBudget",
            strict = true
    )
    public static int BLOCK_ENTITY_TICK_BUDGET = -1;

    @Rule(
            desc = "§7§oSpawn cap multiplier for monsters. §8(1.0 = vanilla)",
            name = "monsterSpawnCapMultiplier"
    )
    public static double MONSTER_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oSpawn cap multiplier for creatures. §8(1.0 = vanilla)",
            name = "creatureSpawnCapMultiplier"
    )
    public static double CREATURE_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oSpawn cap multiplier for ambient mobs. §8(1.0 = vanilla)",
            name = "ambientSpawnCapMultiplier"
    )
    public static double AMBIENT_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oSpawn cap multiplier for water creatures. §8(1.0 = vanilla)",
            name = "waterCreatureSpawnCapMultiplier"
    )
    public static double WATER_CREATURE_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oSpawn cap multiplier for water ambient mobs. §8(1.0 = vanilla)",
            name = "waterAmbientSpawnCapMultiplier"
    )
    public static double WATER_AMBIENT_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oSpawn cap multiplier for misc entities. §8(1.0 = vanilla)",
            name = "miscSpawnCapMultiplier"
    )
    public static double MISC_SPAWN_CAP_MULTIPLIER = 1.0;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 1. §8(example: minecraft:zombie)",
            name = "entityTypeForLimitingProcessing"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 1. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 2.",
            name = "entityTypeForLimitingProcessing2"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING2 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 2. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit2",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT2 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 3.",
            name = "entityTypeForLimitingProcessing3"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING3 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 3. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit3",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT3 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 4.",
            name = "entityTypeForLimitingProcessing4"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING4 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 4. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit4",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT4 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 5.",
            name = "entityTypeForLimitingProcessing5"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING5 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 5. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit5",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT5 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 6.",
            name = "entityTypeForLimitingProcessing6"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING6 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 6. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit6",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT6 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 7.",
            name = "entityTypeForLimitingProcessing7"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING7 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 7. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit7",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT7 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 8.",
            name = "entityTypeForLimitingProcessing8"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING8 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 8. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit8",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT8 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 9.",
            name = "entityTypeForLimitingProcessing9"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING9 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 9. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit9",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT9 = -1;

    @Rule(
            desc = "§7§oEntity type to limit processing for slot 10.",
            name = "entityTypeForLimitingProcessing10"
    )
    public static String ENTITY_TYPE_FOR_LIMITING_PROCESSING10 = "";

    @Rule(
            desc = "§7§oProcessing limit for slot 10. §8(-1 = disabled)",
            name = "entityTypeForLimitingProcessingLimit10",
            strict = true
    )
    public static int ENTITY_TYPE_FOR_LIMITING_PROCESSING_LIMIT10 = -1;

    @Rule(
            desc = "§7§oMerges nearby item entities instantly.",
            name = "instantItemMerge"
    )
    public static boolean INSTANT_ITEM_MERGE = false;

    @Rule(
            desc = "§7§oMaximum distance for item merging.",
            name = "itemMergeRadius",
            strict = true
    )
    public static double ITEM_MERGE_RADIUS = 2.5;

    @Rule(
            desc = "§7§oPlayers automatically pick up items instantly on collision.",
            name = "instantPickup"
    )
    public static boolean INSTANT_PICKUP = false;

    @Rule(
            desc = "§7§oDisables entity collision checks for performance.",
            name = "disableEntityCollision"
    )
    public static boolean DISABLE_ENTITY_COLLISION = false;

    @Rule(
            desc = "§7§oSkips AI ticking for mobs beyond this distance from players.",
            name = "mobAITickRange",
            strict = true
    )
    public static int MOB_AI_TICK_RANGE = -1;

    @Rule(
            desc = "§7§oPrevents mobs from targeting players through walls.",
            name = "noWallTargeting"
    )
    public static boolean NO_WALL_TARGETING = false;

    @Rule(
            desc = "§7§oReduces hopper checks per tick.",
            name = "hopperTransferCooldown",
            strict = true
    )
    public static int HOPPER_TRANSFER_COOLDOWN = 8;

    @Rule(
            desc = "§7§oLimits maximum hopper pull range.",
            name = "hopperPullRange",
            strict = true
    )
    public static int HOPPER_PULL_RANGE = 1;

    @Rule(
            desc = "§7§oEntities only update every X ticks (global throttle).",
            name = "entityTickInterval",
            strict = true
    )
    public static int ENTITY_TICK_INTERVAL = 1;

    @Rule(
            desc = "§7§oBlock updates are batched per tick.",
            name = "blockUpdateThrottle",
            strict = true
    )
    public static int BLOCK_UPDATE_THROTTLE = -1;

    @Rule(
            desc = "§7§oPrevents villagers from pathfinding every tick.",
            name = "villagerBrainInterval",
            strict = true
    )
    public static int VILLAGER_BRAIN_INTERVAL = 20;

    @Rule(
            desc = "§7§oPrevents fire from spreading.",
            name = "disableFireSpread"
    )
    public static boolean DISABLE_FIRE_SPREAD = false;

    @Rule(
            desc = "§7§oPrevents leaf decay.",
            name = "disableLeafDecay"
    )
    public static boolean DISABLE_LEAF_DECAY = false;

    @Rule(
            desc = "§7§oEntities despawn instantly when far beyond simulation distance.",
            name = "hardDespawnDistance",
            strict = true
    )
    public static int HARD_DESPAWN_DISTANCE = -1;

    @Rule(
            desc = "§7§oLimits projectile lifetime.",
            name = "projectileLifetime",
            strict = true
    )
    public static int PROJECTILE_LIFETIME = -1;

    @Rule(
            desc = "§7§oPrevents XP orbs from splitting.",
            name = "noXpSplit"
    )
    public static boolean NO_XP_SPLIT = false;
}
