package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MechanicRegistry {
    private final Map<String, MechanicDefinition> definitions = new ConcurrentHashMap<>();

    public MechanicRegistry() {
        registerDefaults();
    }

    public MechanicDefinition register(MechanicDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        definitions.put(definition.id(), definition);
        return definition;
    }

    public MechanicDefinition register(String id, MechanicValueType type, Object defaultValue,
                                       boolean restartRequired, String description) {
        return register(new MechanicDefinition(id, type, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    public MechanicDefinition register(String id, MechanicValueType type, Object defaultValue,
                                       double minimum, double maximum, boolean restartRequired, String description) {
        return register(new MechanicDefinition(id, type, defaultValue, minimum, maximum, Set.of(), restartRequired, description));
    }

    public MechanicDefinition get(String id) {
        return id == null ? null : definitions.get(id);
    }

    public boolean contains(String id) {
        return id != null && definitions.containsKey(id);
    }

    public MechanicDefinition require(String id) {
        MechanicDefinition definition = get(id);
        if (definition == null) {
            throw new IllegalArgumentException("unknown mechanic: " + id);
        }
        return definition;
    }

    public Object validate(String id, Object value) {
        return require(id).validate(value);
    }

    public Map<String, MechanicDefinition> definitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public Set<String> ids() {
        return definitions().keySet();
    }

    public void unregister(String id) {
        definitions.remove(id);
    }

    private void registerDefaults() {
        bool("day_night_cycle_enabled", true, false, "Allow the day and night cycle");
        bool("time_locked", false, false, "Keep the world time fixed");
        integer("time_of_day", 0L, 0, 24000, false, "Fixed time of day");
        bool("weather_enabled", true, false, "Allow weather changes");
        bool("thunderstorms_enabled", true, false, "Allow thunderstorms");
        enumeration("difficulty", "normal", Set.of("peaceful", "easy", "normal", "hard"), true, "Server difficulty");

        bool("mob_spawning_enabled", true, false, "Allow natural mob spawning");
        bool("passive_spawning_enabled", true, false, "Allow passive mob spawning");
        bool("hostile_spawning_enabled", true, false, "Allow hostile mob spawning");
        bool("ambient_spawning_enabled", true, false, "Allow ambient mob spawning");
        bool("water_mob_spawning_enabled", true, false, "Allow water mob spawning");
        multiplier("mob_damage_multiplier", 1.0, 0.0, 1000.0, false, "Mob damage multiplier");
        multiplier("mob_health_multiplier", 1.0, 0.01, 1000.0, false, "Mob health multiplier");
        multiplier("mob_spawn_cap_multiplier", 1.0, 0.0, 1000.0, false, "Global mob spawn cap multiplier");
        multiplier("passive_spawn_cap_multiplier", 1.0, 0.0, 1000.0, false, "Passive spawn cap multiplier");
        multiplier("hostile_spawn_cap_multiplier", 1.0, 0.0, 1000.0, false, "Hostile spawn cap multiplier");
        multiplier("ambient_spawn_cap_multiplier", 1.0, 0.0, 1000.0, false, "Ambient spawn cap multiplier");
        multiplier("water_spawn_cap_multiplier", 1.0, 0.0, 1000.0, false, "Water spawn cap multiplier");
        bool("phantom_spawning_enabled", true, false, "Allow phantom spawning");
        bool("wandering_trader_enabled", true, false, "Allow wandering trader spawns");
        bool("patrols_enabled", true, false, "Allow patrol spawns");
        bool("raids_enabled", true, false, "Allow raids");
        enumeration("raid_difficulty", "normal", Set.of("peaceful", "easy", "normal", "hard"), true, "Raid difficulty");
        bool("village_mechanics_enabled", true, false, "Allow village mechanics");
        bool("sleep_enabled", true, false, "Allow players to sleep");
        probability("sleep_percentage", 1.0, false, "Fraction of players required to skip night");
        bool("respawning_enabled", true, false, "Allow player respawning");
        bool("spawn_protection_enabled", true, false, "Protect spawn areas");
        integer("spawn_protection_radius", 16, 0, 256, false, "Spawn protection radius");

        bool("natural_health_regeneration_enabled", true, false, "Allow natural health regeneration");
        bool("hunger_enabled", true, false, "Enable hunger");
        number("saturation_multiplier", 1.0, 0.0, 1000.0, false, "Saturation modifier");
        number("exhaustion_multiplier", 1.0, 0.0, 1000.0, false, "Exhaustion modifier");
        multiplier("fall_damage_multiplier", 1.0, 0.0, 1000.0, false, "Fall damage multiplier");
        multiplier("fire_damage_multiplier", 1.0, 0.0, 1000.0, false, "Fire damage multiplier");
        multiplier("drowning_damage_enabled", 1.0, 0.0, 1000.0, false, "Drowning damage multiplier");
        bool("suffocation_damage_enabled", true, false, "Allow suffocation damage");
        multiplier("freezing_damage_multiplier", 1.0, 0.0, 1000.0, false, "Freezing damage multiplier");
        multiplier("berry_damage_multiplier", 1.0, 0.0, 1000.0, false, "Berry damage multiplier");
        multiplier("cactus_damage_multiplier", 1.0, 0.0, 1000.0, false, "Cactus damage multiplier");
        multiplier("void_damage_multiplier", 1.0, 0.0, 1000.0, false, "Void damage multiplier");
        multiplier("explosion_damage_multiplier", 1.0, 0.0, 1000.0, false, "Explosion damage multiplier");
        multiplier("projectile_damage_multiplier", 1.0, 0.0, 1000.0, false, "Projectile damage multiplier");
        bool("entity_collision_enabled", true, false, "Enable entity collision");
        number("knockback_multiplier", 1.0, 0.0, 1000.0, false, "Knockback modifier");
        number("attack_cooldown_multiplier", 1.0, 0.01, 100.0, false, "Attack cooldown modifier");
        bool("shield_behavior_enabled", true, false, "Enable shields");
        bool("critical_hits_enabled", true, false, "Allow critical hits");
        bool("sweeping_attacks_enabled", true, false, "Allow sweeping attacks");
        bool("pvp_enabled", true, false, "Allow player versus player damage");
        bool("pve_enabled", true, false, "Allow player versus environment damage");
        bool("friendly_fire_enabled", false, false, "Allow friendly fire");
        bool("projectile_interaction_enabled", true, false, "Allow projectile interactions");
        bool("trident_mechanics_enabled", true, false, "Enable trident mechanics");
        bool("bow_mechanics_enabled", true, false, "Enable bow mechanics");
        bool("crossbow_mechanics_enabled", true, false, "Enable crossbow mechanics");

        bool("fishing_enabled", true, false, "Allow fishing");
        bool("fishing_loot_enabled", true, false, "Allow fishing loot");
        duration("fishing_timing", 20L, 1, 1_200_000, false, "Fishing interval");
        bool("item_pickup_enabled", true, false, "Allow item pickup");
        bool("item_merging_enabled", false, false, "Allow item entity merging");
        number("item_merge_radius", 2.5, 0.1, 64.0, false, "Item merge radius");
        duration("item_despawn_time", 6000L, -1, 1_728_000, false, "Item despawn time in ticks");
        multiplier("xp_drop_multiplier", 1.0, 0.0, 1000.0, false, "Experience drop multiplier");
        bool("xp_pickup_enabled", true, false, "Allow experience pickup");
        bool("xp_splitting_enabled", true, false, "Allow experience splitting");
        multiplier("xp_multiplier", 1.0, 0.0, 1000.0, false, "Experience multiplier");
        bool("entity_despawning_enabled", true, false, "Allow natural entity despawning");
        bool("entity_activation_enabled", true, false, "Allow entity activation");
        bool("mob_ai_enabled", true, false, "Enable mob AI");
        integer("mob_ai_tick_range", -1, -1, 1_728_000, false, "Mob AI activation range");
        duration("mob_ai_interval", 1L, 1, 1200, false, "Mob AI processing interval");
        bool("mob_targeting_enabled", true, false, "Enable mob targeting");
        bool("mob_pathfinding_enabled", true, false, "Enable mob pathfinding");
        bool("villager_ai_enabled", true, false, "Enable villager AI");
        bool("villager_restocking_enabled", true, false, "Enable villager restocking");
        bool("villager_breeding_enabled", true, false, "Allow villager breeding");
        bool("villager_trading_enabled", true, false, "Allow villager trading");
        bool("wandering_trader_trading_enabled", true, false, "Allow wandering trader trading");
        bool("breeding_enabled", true, false, "Allow breeding");
        bool("animal_growth_enabled", true, false, "Allow animal growth");
        bool("crop_growth_enabled", true, false, "Allow crop growth");
        bool("tree_growth_enabled", true, false, "Allow tree growth");
        bool("random_ticks_enabled", true, false, "Allow random ticks");
        bool("fire_spread_enabled", true, false, "Allow fire spread");
        bool("leaf_decay_enabled", true, false, "Allow leaf decay");
        bool("ice_melting_enabled", true, false, "Allow ice melting");
        bool("snow_melting_enabled", true, false, "Allow snow melting");
        bool("grass_spreading_enabled", true, false, "Allow grass spreading");
        bool("fluid_behavior_enabled", true, false, "Enable fluid behavior");
        bool("lava_behavior_enabled", true, false, "Enable lava behavior");
        bool("water_behavior_enabled", true, false, "Enable water behavior");
        bool("portal_creation_enabled", true, false, "Allow portal creation");
        bool("portal_travel_enabled", true, false, "Allow portal travel");
        bool("nether_travel_enabled", true, false, "Allow nether travel");
        bool("end_travel_enabled", true, false, "Allow end travel");
        bool("respawn_anchor_enabled", true, false, "Allow respawn anchors");
        bool("beds_in_unsafe_dimensions_enabled", false, false, "Allow beds in unsafe dimensions");

        bool("explosions_enabled", true, false, "Allow explosions");
        bool("tnt_enabled", true, false, "Allow TNT");
        bool("creepers_enabled", true, false, "Allow creeper explosions");
        bool("end_crystals_enabled", true, false, "Allow end crystal explosions");
        bool("beds_explosive_enabled", true, false, "Allow beds to explode");
        multiplier("explosion_power_multiplier", 1.0, 0.0, 1000.0, false, "Explosion power modifier");
        multiplier("block_break_speed_multiplier", 1.0, 0.01, 1000.0, false, "Block break speed modifier");
        bool("block_breaking_enabled", true, false, "Allow block breaking");
        bool("block_placement_enabled", true, false, "Allow block placement");
        bool("block_interaction_enabled", true, false, "Allow block interaction");
        bool("container_interaction_enabled", true, false, "Allow container interaction");
        bool("hopper_transfer_enabled", true, false, "Allow hopper transfers");
        integer("hopper_pull_range", 1, 0, 64, false, "Hopper pull range");
        duration("hopper_cooldown", 8L, 0, 1_200_000, false, "Hopper transfer cooldown");
        bool("comparator_behavior_enabled", true, false, "Enable comparator behavior");
        bool("dispenser_behavior_enabled", true, false, "Enable dispenser behavior");
        bool("dropper_behavior_enabled", true, false, "Enable dropper behavior");
        bool("crafter_behavior_enabled", true, false, "Enable crafter behavior");
        number("furnace_speed_multiplier", 1.0, 0.01, 1000.0, false, "Furnace speed modifier");
        number("blast_furnace_speed_multiplier", 1.0, 0.01, 1000.0, false, "Blast furnace speed modifier");
        number("smoker_speed_multiplier", 1.0, 0.01, 1000.0, false, "Smoker speed modifier");
        number("campfire_speed_multiplier", 1.0, 0.01, 1000.0, false, "Campfire speed modifier");
        number("brewing_speed_multiplier", 1.0, 0.01, 1000.0, false, "Brewing speed modifier");
        bool("crafting_enabled", true, false, "Allow crafting");
        bool("recipes_enabled", true, true, "Enable recipe system");
        bool("smelting_recipes_enabled", true, true, "Enable smelting recipes");
        bool("blasting_recipes_enabled", true, true, "Enable blasting recipes");
        bool("smoking_recipes_enabled", true, true, "Enable smoking recipes");
        bool("stonecutting_recipes_enabled", true, true, "Enable stonecutting recipes");
        bool("smithing_recipes_enabled", true, true, "Enable smithing recipes");
        bool("enchanting_enabled", true, false, "Allow enchanting");
        bool("anvil_behavior_enabled", true, false, "Enable anvils");
        bool("grindstone_behavior_enabled", true, false, "Enable grindstones");
        bool("item_damage_enabled", true, false, "Allow item durability damage");
        bool("item_repair_enabled", true, false, "Allow item repair");
        bool("mending_enabled", true, false, "Allow mending");
        integer("enchantment_level_cap", 255, 0, 255, false, "Enchantment level cap");
        number("potion_duration_multiplier", 1.0, 0.0, 1000.0, false, "Potion duration modifier");
        integer("potion_amplifier_cap", 255, 0, 255, false, "Potion amplifier cap");
        bool("status_effects_enabled", true, false, "Allow status effects");
        bool("beacon_behavior_enabled", true, true, "Enable beacon behavior");
        bool("conduit_behavior_enabled", true, true, "Enable conduit behavior");

        bool("loot_drops_enabled", true, false, "Allow loot drops");
        multiplier("loot_drop_multiplier", 1.0, 0.0, 1000.0, false, "Loot count multiplier");
        multiplier("xp_drop_multiplier_entity", 1.0, 0.0, 1000.0, false, "Entity experience multiplier");
        bool("silk_touch_behavior_enabled", true, false, "Enable silk touch behavior");
        bool("fortune_behavior_enabled", true, false, "Enable fortune behavior");
        bool("player_kill_drops_only", false, false, "Drop loot only for player kills");
        bool("environment_drops_enabled", true, false, "Allow environment drops");
        bool("event_drops_enabled", true, false, "Allow event drops");

        bool("entity_persistence_enabled", true, false, "Allow entity persistence");
        bool("entity_collision_enabled", true, false, "Enable entity collisions");
        bool("entity_gravity_enabled", true, false, "Enable entity gravity");
        bool("entity_fire_immunity_enabled", false, false, "Make configured entities fire immune");
        bool("entity_pickup_enabled", true, false, "Allow entity pickup");
        number("entity_max_health_multiplier", 1.0, 0.01, 1000.0, false, "Entity max health modifier");
        number("entity_attack_damage_multiplier", 1.0, 0.0, 1000.0, false, "Entity attack damage modifier");
        number("entity_movement_speed_multiplier", 1.0, 0.01, 1000.0, false, "Entity movement speed modifier");
        number("entity_follow_range_multiplier", 1.0, 0.0, 1000.0, false, "Entity follow range modifier");
        number("entity_damage_taken_multiplier", 1.0, 0.0, 1000.0, false, "Entity damage taken modifier");
        number("entity_explosion_power_multiplier", 1.0, 0.0, 1000.0, false, "Entity explosion power modifier");
        probability("entity_spawn_probability", 1.0, false, "Entity spawn probability");
        integer("entity_spawn_cap", -1, -1, 10_000_000, false, "Entity spawn cap override");
        number("entity_despawn_distance", -1.0, -1.0, 1_728_000.0, false, "Entity despawn distance override");
        duration("entity_despawn_time", -1L, -1, 1_728_000, false, "Entity despawn time override");
        bool("entity_persistent", false, false, "Keep configured entities persistent");
        bool("entity_ai_enabled", true, false, "Enable configured entity AI");
        map("entity_targeting_rules", Map.of(), false, "Entity targeting rules");
        map("entity_equipment", Map.of(), false, "Entity equipment rules");
        map("entity_drops", Map.of(), false, "Entity drop rules");
        map("entity_xp", Map.of(), false, "Entity experience rules");
        bool("entity_fire_immune", false, false, "Make configured entities fire immune");
        bool("entity_pickup_behavior", true, false, "Allow configured entity pickup");
        string("entity_explosion_power", "1.0", false, "Entity explosion power");
        bool("entity_glowing", false, false, "Make configured entities glowing");
        string("entity_custom_name", "", false, "Configured entity custom name");

        integer("stack_size_override", 64, 1, 2304, true, "Maximum stack size override");
        duration("item_use_cooldown", 0L, 0, 1_728_000, false, "Item use cooldown");
        duration("item_use_duration", 0L, 0, 1_728_000, false, "Item use duration");
        number("item_damage_multiplier", 1.0, 0.0, 1000.0, false, "Item durability damage modifier");
        number("item_mining_speed_multiplier", 1.0, 0.01, 1000.0, false, "Item mining speed modifier");
        number("item_attack_speed_multiplier", 1.0, 0.01, 1000.0, false, "Item attack speed modifier");
        number("item_sell_value_multiplier", 1.0, 0.0, 1000000.0, false, "Item sell value modifier");
        list("item_allowed_categories", List.of(), false, "Allowed item categories");
        list("item_blocked_categories", List.of(), false, "Blocked item categories");
        list("item_allowed_ids", List.of(), false, "Allowed item ids");
        list("item_blocked_ids", List.of(), false, "Blocked item ids");
        list("item_allowed_tags", List.of(), false, "Allowed item tags");
        list("item_blocked_tags", List.of(), false, "Blocked item tags");
        integer("item_stack_size", 64, 1, 2304, true, "Configured item stack size");
        integer("item_durability", -1, -1, 1_000_000, false, "Configured item durability");
        number("item_repairability", 1.0, 0.0, 1000.0, false, "Configured item repairability");
        number("item_damage", 0.0, 0.0, 1000.0, false, "Configured item damage");
        number("item_mining_speed", 1.0, 0.01, 1000.0, false, "Configured item mining speed");
        number("item_attack_speed", 1.0, 0.01, 1000.0, false, "Configured item attack speed");
        map("item_attributes", Map.of(), false, "Configured item attributes");
        map("item_enchantment_restrictions", Map.of(), false, "Configured item enchantment restrictions");
        number("item_sell_value", 0.0, 0.0, 1_000_000_000.0, false, "Configured item sell value");
        multiplier("item_drop_multiplier", 1.0, 0.0, 1000.0, false, "Configured item drop multiplier");
        map("item_crafting_restrictions", Map.of(), false, "Configured item crafting restrictions");
        map("item_container_behavior", Map.of(), false, "Configured item container behavior");
        list("item_categories", List.of(), false, "Configured item categories");
        list("item_ids", List.of(), false, "Configured item ids");
        list("item_tags", List.of(), false, "Configured item tags");

        number("block_hardness_multiplier", 1.0, 0.01, 1000.0, false, "Block hardness modifier");
        number("block_explosion_resistance_multiplier", 1.0, 0.0, 1000.0, false, "Block explosion resistance modifier");
        bool("block_piston_behavior_enabled", true, false, "Enable piston behavior");
        bool("block_redstone_behavior_enabled", true, false, "Enable redstone behavior");
        bool("block_fluid_interaction_enabled", true, false, "Enable fluid interactions");
        bool("block_fire_interaction_enabled", true, false, "Enable fire interactions");
        bool("block_growth_enabled", true, false, "Allow block growth");
        bool("block_decay_enabled", true, false, "Allow block decay");
        bool("block_container_open_enabled", true, false, "Allow containers to open");
        list("block_allowed_ids", List.of(), false, "Allowed block ids");
        list("block_blocked_ids", List.of(), false, "Blocked block ids");
        list("block_allowed_tags", List.of(), false, "Allowed block tags");
        list("block_blocked_tags", List.of(), false, "Blocked block tags");
        number("block_hardness", -1.0, -1.0, 1_000_000.0, false, "Configured block hardness");
        number("block_break_time", -1.0, -1.0, 1_000_000.0, false, "Configured block break time");
        number("block_explosion_resistance", -1.0, -1.0, 1_000_000.0, false, "Configured block explosion resistance");
        bool("block_interaction", true, false, "Configured block interaction");
        bool("block_placement", true, false, "Configured block placement");
        bool("block_piston_behavior", true, false, "Configured block piston behavior");
        bool("block_redstone_behavior", true, false, "Configured block redstone behavior");
        bool("block_random_tick", true, false, "Configured block random ticking");
        bool("block_fluid_interaction", true, false, "Configured block fluid interaction");
        bool("block_fire_interaction", true, false, "Configured block fire interaction");
        map("block_drops", Map.of(), false, "Configured block drops");
        map("block_xp", Map.of(), false, "Configured block experience");
        bool("block_growth", true, false, "Configured block growth");
        bool("block_decay", true, false, "Configured block decay");
        bool("block_container_open", true, false, "Configured container opening");
        map("block_container_restrictions", Map.of(), false, "Configured container restrictions");
        list("block_ids", List.of(), false, "Configured block ids");
        list("block_tags", List.of(), false, "Configured block tags");

        multiplier("drop_multiplier", 1.0, 0.0, 1000.0, false, "General drop multiplier");
        bool("drop_removal", false, false, "Remove configured drops");
        map("drop_replacement", Map.of(), false, "Configured drop replacements");
        multiplier("drop_chance_multiplier", 1.0, 0.0, 1000.0, false, "Drop chance multiplier");
        map("drop_conditions", Map.of(), false, "Conditional drop rules");
        multiplier("block_xp_multiplier", 1.0, 0.0, 1000.0, false, "Block experience multiplier");
        map("entity_drop_rules", Map.of(), false, "Entity drop rule set");
        map("block_drop_rules", Map.of(), false, "Block drop rule set");

        map("entity_rules", Map.of(), false, "Entity-specific rules");
        map("item_rules", Map.of(), false, "Item-specific rules");
        map("block_rules", Map.of(), false, "Block-specific rules");
        map("world_rules", Map.of(), false, "World-specific rules");
        map("custom_mechanic_data", Map.of(), false, "Custom nested mechanic data");

        integer("entity_tick_budget", -1L, -1, 1_000_000, false, "Entity processing budget");
        integer("block_entity_tick_budget", -1L, -1, 1_000_000, false, "Block entity processing budget");
        duration("entity_tick_interval", 1L, 1, 1200, false, "Entity processing interval");
        duration("entity_activation_range", -1L, -1, 1_728_000, false, "Entity activation range override");
        integer("max_entity_count", -1, -1, 10_000_000, false, "Maximum entity count");
        integer("max_item_entity_count", -1, -1, 10_000_000, false, "Maximum item entity count");
        integer("max_block_entity_count", -1, -1, 10_000_000, false, "Maximum block entity count");
        bool("safe_mode", false, false, "Restrictive emergency profile");

        register("drop_at_feet_radius", MechanicValueType.INTEGER, 0L, 0, 256, false, "Drop-at-feet radius");
        register("experience_orb_range", MechanicValueType.NUMBER, 64.0, 0.0, 256.0, false, "Experience orb range");
        register("furnace_cooking_speed", MechanicValueType.INTEGER, 200L, 1, 1_200_000, false, "Furnace cooking speed");
        register("monster_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Monster spawn cap multiplier");
        register("creature_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Creature spawn cap multiplier");
        register("ambient_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Ambient spawn cap multiplier");
        register("water_creature_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Water creature spawn cap multiplier");
        register("water_ambient_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Water ambient spawn cap multiplier");
        register("misc_spawn_cap_multiplier", MechanicValueType.MULTIPLIER, 1.0, 0.0, 1000.0, false, "Misc spawn cap multiplier");
        register("no_xp_split", MechanicValueType.BOOLEAN, false, false, "Disable experience orb splitting");
    }

    private void bool(String id, boolean defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.BOOLEAN, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    private void integer(String id, long defaultValue, double minimum, double maximum, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.INTEGER, defaultValue, minimum, maximum, Set.of(), restartRequired, description));
    }

    private void number(String id, double defaultValue, double minimum, double maximum, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.NUMBER, defaultValue, minimum, maximum, Set.of(), restartRequired, description));
    }

    private void multiplier(String id, double defaultValue, double minimum, double maximum, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.MULTIPLIER, defaultValue, minimum, maximum, Set.of(), restartRequired, description));
    }

    private void duration(String id, long defaultValue, double minimum, double maximum, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.DURATION, defaultValue, minimum, maximum, Set.of(), restartRequired, description));
    }

    private void probability(String id, double defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.PROBABILITY, defaultValue, 0.0, 1.0, Set.of(), restartRequired, description));
    }

    private void enumeration(String id, String defaultValue, Set<String> values, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.ENUM, defaultValue, null, null, values, restartRequired, description));
    }

    private void string(String id, String defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.STRING, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    private void list(String id, java.util.List<?> defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.LIST, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    private void map(String id, Map<String, ?> defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, MechanicValueType.MAP, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    private void register(MechanicValueType type, String id, Object defaultValue, boolean restartRequired, String description) {
        register(new MechanicDefinition(id, type, defaultValue, null, null, Set.of(), restartRequired, description));
    }

    private double minimum() {
        return 0.0;
    }
}
