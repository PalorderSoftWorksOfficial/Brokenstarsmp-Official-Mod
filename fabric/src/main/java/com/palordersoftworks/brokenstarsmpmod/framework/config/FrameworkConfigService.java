package com.palordersoftworks.brokenstarsmpmod.framework.config;

import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicContext;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicDefinition;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicRegistry;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicResolver;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicRule;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicScope;
import com.palordersoftworks.brokenstarsmpmod.framework.mechanics.MechanicValueType;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FrameworkConfigService {
    public static final int CONFIG_VERSION = 1;
    public static final String MECHANICS_FILE = "mechanics.yml";
    public static final String ENTITIES_FILE = "entities.yml";
    public static final String ITEMS_FILE = "items.yml";
    public static final String BLOCKS_FILE = "blocks.yml";
    public static final String WORLDS_FILE = "worlds.yml";
    public static final String EVENTS_FILE = "events.yml";
    public static final String PROFILES_FILE = "profiles.yml";
    public static final String PERMISSIONS_FILE = "permissions.yml";
    public static final String SERVER_RULES_FILE = "server-rules.yml";
    public static final String TEAM_RULES_FILE = "team-rules.yml";
    public static final String UNSTABLE_RULES_FILE = "unstable-smp.yml";

    private static final List<String> FILES = List.of(
            SERVER_RULES_FILE, MECHANICS_FILE, ENTITIES_FILE, ITEMS_FILE, BLOCKS_FILE, WORLDS_FILE,
            EVENTS_FILE, PROFILES_FILE, PERMISSIONS_FILE, TEAM_RULES_FILE, UNSTABLE_RULES_FILE
    );

    private final Path directory;
    private final MechanicRegistry registry;
    private final MechanicResolver resolver;
    private final Yaml yaml;
    private final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
    private final List<ConfigIssue> issues = new ArrayList<>();
    private final List<MechanicRule> configuredRules = new ArrayList<>();
    private final Set<String> loadedFiles = new LinkedHashSet<>();
    private final Set<String> restartRequired = new LinkedHashSet<>();
    private volatile String activeProfile = "default";
    private volatile Instant lastReload = Instant.EPOCH;
    private volatile int loadedRuleCount;

    public FrameworkConfigService(MechanicRegistry registry, MechanicResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
        this.directory = FabricLoader.getInstance().getConfigDir().resolve("brokenstarsmp");
        this.yaml = createYaml();
        ensureFiles();
    }

    public synchronized ReloadResult reload() {
        return reloadInternal(null);
    }

    public synchronized ReloadResult reload(String section) {
        return reloadInternal(section == null ? null : section.trim().toLowerCase(Locale.ROOT));
    }

    public Path directory() {
        return directory;
    }

    public Path file(String name) {
        return directory.resolve(name);
    }

    public MechanicRegistry registry() {
        return registry;
    }

    public MechanicResolver resolver() {
        return resolver;
    }

    public Map<String, Object> document(String name) {
        Map<String, Object> document = documents.get(name);
        return document == null ? Map.of() : Collections.unmodifiableMap(document);
    }

    public List<MechanicRule> configuredRules() {
        return List.copyOf(configuredRules);
    }

    public List<ConfigIssue> issues() {
        return List.copyOf(issues);
    }

    public Set<String> loadedFiles() {
        return Set.copyOf(loadedFiles);
    }

    public Set<String> restartRequired() {
        return Set.copyOf(restartRequired);
    }

    public String activeProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        if (activeProfile != null && !activeProfile.isBlank()) {
            this.activeProfile = activeProfile.trim();
        }
    }

    public Instant lastReload() {
        return lastReload;
    }

    public int configVersion() {
        return CONFIG_VERSION;
    }

    public int loadedRuleCount() {
        return loadedRuleCount;
    }

    public int auditRetention() {
        Object value = value(PERMISSIONS_FILE, "audit", "retention_entries");
        if (value instanceof Number number) {
            return Math.max(100, Math.min(1_000_000, number.intValue()));
        }
        return 5000;
    }

    public Map<String, Object> permissionNodes() {
        Object value = value(PERMISSIONS_FILE, "nodes");
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        return Map.of();
    }

    public void report(ConfigIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    public void writeDocument(String name, Map<String, Object> document) {
        Path target = file(name);
        try {
            Files.createDirectories(target.getParent());
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                yaml.dump(document, writer);
            }
            documents.put(name, new LinkedHashMap<>(document));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write configuration " + target, exception);
        }
    }

    private ReloadResult reloadInternal(String section) {
        issues.clear();
        configuredRules.clear();
        loadedFiles.clear();
        restartRequired.clear();
        documents.clear();
        ensureFiles();

        for (String name : FILES) {
            if (section != null && !matchesSection(section, name)) {
                Map<String, Object> existing = readDocument(name);
                if (!existing.isEmpty()) {
                    documents.put(name, existing);
                    loadedFiles.add(name);
                }
                continue;
            }
            Map<String, Object> document = readDocument(name);
            documents.put(name, document);
            loadedFiles.add(name);
            if (document.isEmpty() && !Files.exists(file(name))) {
                issues.add(ConfigIssue.warning(name, "", "", "", "mapping", "defaults", "file is empty"));
            }
        }

        loadDefinitions();
        loadRules();
        resolver.replaceLayer(0, configuredRules.stream()
                .map(rule -> rule.toOverride(nameForRule(rule), 0))
                .toList());
        lastReload = Instant.now();
        loadedRuleCount = configuredRules.size();
        return new ReloadResult(issues.stream().noneMatch(issue -> issue.severity() == ConfigIssue.Severity.ERROR),
                Set.copyOf(loadedFiles), loadedRuleCount, List.copyOf(issues), Set.copyOf(restartRequired),
                activeProfile, lastReload, CONFIG_VERSION);
    }

    private void loadDefinitions() {
        Map<String, Object> definitions = mapValue(MECHANICS_FILE, "definitions");
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                addIssue(ConfigIssue.error(MECHANICS_FILE, "definitions", id, entry.getValue(), "mapping", "built-in definition", "definition must be a mapping"));
                continue;
            }
            Map<String, Object> values = stringMap(raw);
            try {
                MechanicValueType type = MechanicValueType.valueOf(String.valueOf(values.getOrDefault("type", "string")).toUpperCase(Locale.ROOT));
                Object defaultValue = values.get("default");
                Double minimum = numberValue(values.get("minimum"));
                Double maximum = numberValue(values.get("maximum"));
                Set<String> allowed = stringSet(values.get("allowed"));
                boolean restart = Boolean.TRUE.equals(values.getOrDefault("restart_required", false));
                String description = String.valueOf(values.getOrDefault("description", ""));
                registry.register(new MechanicDefinition(id, type, defaultValue, minimum, maximum, allowed, restart, description));
            } catch (RuntimeException exception) {
                addIssue(ConfigIssue.error(MECHANICS_FILE, "definitions", id, entry.getValue(), "valid mechanic definition", "built-in definition", exception.getMessage()));
            }
        }
    }

    private void loadRules() {
        parseScope(MECHANICS_FILE, "global", MechanicScope.GLOBAL);
        parseScope(MECHANICS_FILE, "dimensions", MechanicScope.DIMENSION);
        parseScope(MECHANICS_FILE, "worlds", MechanicScope.WORLD);
        parseScope(MECHANICS_FILE, "regions", MechanicScope.REGION);
        parseScope(MECHANICS_FILE, "permissions", MechanicScope.PERMISSION);
        parseScope(MECHANICS_FILE, "ranks", MechanicScope.RANK);
        parseScope(MECHANICS_FILE, "teams", MechanicScope.TEAM);
        parseScope(MECHANICS_FILE, "players", MechanicScope.PLAYER);
        parseScope(MECHANICS_FILE, "items", MechanicScope.ITEM);
        parseScope(MECHANICS_FILE, "entities", MechanicScope.ENTITY);
        parseScope(ENTITIES_FILE, "entities", MechanicScope.ENTITY);
        parseScope(ITEMS_FILE, "items", MechanicScope.ITEM);
        parseScope(BLOCKS_FILE, "blocks", MechanicScope.BLOCK);
        parseScope(WORLDS_FILE, "worlds", MechanicScope.WORLD);
        parseLegacy(SERVER_RULES_FILE, "server-rules");
        parseLegacy(TEAM_RULES_FILE, "team-rules");
        parseLegacy(UNSTABLE_RULES_FILE, "unstable-smp");
    }

    private void parseScope(String file, String section, MechanicScope scope) {
        Object raw = value(file, section);
        if (!(raw instanceof Map<?, ?> values)) {
            return;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String selectorName) || !(entry.getValue() instanceof Map<?, ?> selectorValues)) {
                addIssue(ConfigIssue.warning(file, section, String.valueOf(entry.getKey()), entry.getValue(), "selector mapping", "ignored", "selector must contain a mechanic mapping"));
                continue;
            }
            MechanicContext selector = selector(scope, selectorName);
            parseRuleMap(file, section + "." + selectorName, stringMap(selectorValues), selector);
        }
    }

    private void parseRuleMap(String file, String section, Map<String, Object> values, MechanicContext selector) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.equals("overrides") || key.equals("mechanics") || key.equals("rules")) {
                if (entry.getValue() instanceof Map<?, ?> nested) {
                    parseRuleMap(file, section + "." + key, stringMap(nested), selector);
                } else {
                    addIssue(ConfigIssue.error(file, section, key, entry.getValue(), "mapping", "ignored", "override container must be a mapping"));
                }
                continue;
            }
            addRule(file, section, key, entry.getValue(), selector);
        }
    }

    private void addRule(String file, String section, String key, Object rawValue, MechanicContext selector) {
        MechanicDefinition definition = registry.get(key);
        if (definition == null) {
            addIssue(ConfigIssue.error(file, section, key, rawValue, "registered mechanic id", "value ignored", "mechanic is not registered"));
            return;
        }
        try {
            Object value = definition.validate(rawValue);
            MechanicRule rule = new MechanicRule(key, value, selector, file, section,
                    selector.scope().specificity(), definition.restartRequired());
            configuredRules.add(rule);
            if (definition.restartRequired()) {
                restartRequired.add(key);
            }
        } catch (RuntimeException exception) {
            addIssue(ConfigIssue.error(file, section, key, rawValue,
                    definition.type().serializedName() + boundsText(definition), String.valueOf(definition.defaultValue()),
                    exception.getMessage()));
        }
    }

    private void parseLegacy(String file, String section) {
        Object raw = documents.get(file);
        if (!(raw instanceof Map<?, ?> values)) {
            return;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            String mapped = legacyId(key);
            if (mapped == null) {
                continue;
            }
            MechanicDefinition definition = registry.get(mapped);
            if (definition == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Boolean booleanValue && mapped.endsWith("_enabled") && legacyInverse(key)) {
                value = !booleanValue;
            }
            addRule(file, section, key, value, MechanicContext.global());
        }
    }

    private void addIssue(ConfigIssue issue) {
        issues.add(issue);
    }

    private Map<String, Object> readDocument(String name) {
        Path path = file(name);
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object parsed = yaml.load(reader);
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            if (!(parsed instanceof Map<?, ?> map)) {
                addIssue(ConfigIssue.error(name, "", "", parsed, "YAML mapping", "defaults", "root must be a mapping"));
                return new LinkedHashMap<>();
            }
            return stringMap(map);
        } catch (IOException | RuntimeException exception) {
            addIssue(ConfigIssue.error(name, "", "", "", "valid YAML mapping", "defaults", exception.getMessage()));
            return new LinkedHashMap<>();
        }
    }

    private void ensureFiles() {
        try {
            Files.createDirectories(directory);
            for (String name : FILES) {
                Path path = file(name);
                if (!Files.exists(path)) {
                    writeDefault(name, path);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create BrokenStars configuration directory", exception);
        }
    }

    private void writeDefault(String name, Path path) throws IOException {
        Map<String, Object> defaults = defaultDocument(name);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            yaml.dump(defaults, writer);
        }
    }

    private Map<String, Object> defaultDocument(String name) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", CONFIG_VERSION);
        if (name.equals(MECHANICS_FILE)) {
            root.put("definitions", new LinkedHashMap<>());
            root.put("global", defaultMechanics());
            root.put("dimensions", new LinkedHashMap<>());
            root.put("worlds", new LinkedHashMap<>());
            root.put("regions", new LinkedHashMap<>());
            root.put("permissions", new LinkedHashMap<>());
            root.put("ranks", new LinkedHashMap<>());
            root.put("teams", new LinkedHashMap<>());
            root.put("players", new LinkedHashMap<>());
            root.put("entities", new LinkedHashMap<>());
            root.put("items", new LinkedHashMap<>());
        } else if (name.equals(ENTITIES_FILE)) {
            root.put("entities", new LinkedHashMap<>());
        } else if (name.equals(ITEMS_FILE)) {
            root.put("items", new LinkedHashMap<>());
        } else if (name.equals(BLOCKS_FILE)) {
            root.put("blocks", new LinkedHashMap<>());
        } else if (name.equals(WORLDS_FILE)) {
            root.put("worlds", new LinkedHashMap<>());
        } else if (name.equals(EVENTS_FILE)) {
            root.put("events", defaultEvents());
        } else if (name.equals(PROFILES_FILE)) {
            root.put("active", "default");
            root.put("profiles", defaultProfiles());
        } else if (name.equals(PERMISSIONS_FILE)) {
            root.put("nodes", defaultPermissionNodes());
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("retention_entries", 5000);
            root.put("audit", audit);
        } else if (name.equals(SERVER_RULES_FILE)) {
            root.putAll(defaultMechanics());
        } else if (name.equals(TEAM_RULES_FILE)) {
            root.put("teamsEnabled", true);
            root.put("teamMaxMembers", 0);
            root.put("teamFriendlyFire", false);
            root.put("teamProjectileFriendlyFire", false);
            root.put("teamNoPvp", false);
        } else if (name.equals(UNSTABLE_RULES_FILE)) {
            root.put("immortalSystemEnabled", true);
            root.put("smartTotemDetection", true);
            root.put("deathBanEnabled", true);
        }
        return root;
    }

    private Map<String, Object> defaultMechanics() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mob_damage_multiplier", 1.0);
        values.put("mob_health_multiplier", 1.0);
        values.put("xp_multiplier", 1.0);
        values.put("pvp_enabled", true);
        values.put("item_drops_enabled", true);
        values.put("explosions_enabled", true);
        values.put("mob_spawning_enabled", true);
        values.put("entity_tick_budget", -1);
        values.put("block_entity_tick_budget", -1);
        return values;
    }

    private Map<String, Object> defaultEvents() {
        Map<String, Object> events = new LinkedHashMap<>();
        Map<String, Object> bloodMoon = new LinkedHashMap<>();
        bloodMoon.put("enabled", false);
        bloodMoon.put("duration", "10m");
        bloodMoon.put("cooldown", "30m");
        bloodMoon.put("priority", 100);
        bloodMoon.put("overrides", Map.of("hostile_spawn_multiplier", 1.5, "mob_damage_multiplier", 1.25));
        bloodMoon.put("messages", List.of("<red>Blood Moon begins."));
        bloodMoon.put("commands", List.of());
        bloodMoon.put("lua_callbacks", List.of());
        events.put("blood_moon", bloodMoon);
        return events;
    }

    private Map<String, Object> defaultProfiles() {
        Map<String, Object> profiles = new LinkedHashMap<>();
        Map<String, Object> defaultProfile = new LinkedHashMap<>();
        defaultProfile.put("description", "Default server profile");
        defaultProfile.put("overrides", new LinkedHashMap<>());
        profiles.put("default", defaultProfile);
        Map<String, Object> peaceful = new LinkedHashMap<>();
        peaceful.put("extends", "default");
        peaceful.put("overrides", Map.of("hostile_spawning_enabled", false));
        profiles.put("peaceful", peaceful);
        Map<String, Object> pvp = new LinkedHashMap<>();
        pvp.put("extends", "default");
        pvp.put("overrides", Map.of("pvp_enabled", true));
        profiles.put("pvp", pvp);
        return profiles;
    }

    private Map<String, Object> defaultPermissionNodes() {
        Map<String, Object> nodes = new LinkedHashMap<>();
        nodes.put("gamemaster", "brokenstarsmp.admin");
        nodes.put("administrator", "brokenstarsmp.administrator");
        nodes.put("moderator", "brokenstarsmp.moderator");
        nodes.put("event_manager", "brokenstarsmp.event.manager");
        nodes.put("mechanic_manager", "brokenstarsmp.mechanic.manager");
        nodes.put("economy_manager", "brokenstarsmp.economy.manager");
        nodes.put("script_manager", "brokenstarsmp.script.manager");
        nodes.put("config_manager", "brokenstarsmp.config.manager");
        nodes.put("player_vault", "brokenstarsmp.economy.playervault");
        nodes.put("player_vault_scale", "brokenstarsmp.economy.playervault.scale");
        nodes.put("checkhacks_admin", "brokenstarsmp.checkhacks.admin");
        nodes.put("audit", "brokenstarsmp.audit");
        return nodes;
    }

    private boolean matchesSection(String section, String file) {
        return switch (section) {
            case "all", "config", "configs" -> true;
            case "server", "server rules", "server-rules", "server_rules" -> file.equals(SERVER_RULES_FILE);
            case "mechanic", "mechanics" -> file.equals(MECHANICS_FILE);
            case "entit", "entities" -> file.equals(ENTITIES_FILE) || file.equals(MECHANICS_FILE);
            case "item", "items" -> file.equals(ITEMS_FILE) || file.equals(MECHANICS_FILE);
            case "block", "blocks" -> file.equals(BLOCKS_FILE) || file.equals(MECHANICS_FILE);
            case "world", "worlds" -> file.equals(WORLDS_FILE) || file.equals(MECHANICS_FILE);
            case "event", "events" -> file.equals(EVENTS_FILE);
            case "profile", "profiles" -> file.equals(PROFILES_FILE);
            case "permission", "permissions" -> file.equals(PERMISSIONS_FILE);
            case "team", "teams" -> file.equals(TEAM_RULES_FILE) || file.equals(MECHANICS_FILE);
            case "unstable", "unstable-smp" -> file.equals(UNSTABLE_RULES_FILE);
            default -> false;
        };
    }

    private MechanicContext selector(MechanicScope scope, String name) {
        return switch (scope) {
            case GLOBAL -> MechanicContext.global();
            case DIMENSION -> MechanicContext.builder().scope(scope).dimension(name).build();
            case WORLD -> MechanicContext.builder().scope(scope).world(name).build();
            case REGION -> MechanicContext.builder().scope(scope).region(name).build();
            case BLOCK -> MechanicContext.builder().scope(scope).block(name).build();
            case PERMISSION -> MechanicContext.builder().scope(scope).permission(name).build();
            case RANK -> MechanicContext.builder().scope(scope).rank(name).build();
            case TEAM -> MechanicContext.builder().scope(scope).team(name).build();
            case PLAYER -> MechanicContext.builder().scope(scope).player(name).build();
            case ENTITY -> MechanicContext.builder().scope(scope).entity(name).build();
            case ITEM -> MechanicContext.builder().scope(scope).item(name).build();
        };
    }

    private Object value(String file, String... path) {
        Map<String, Object> document = documents.get(file);
        Object current = document;
        for (String part : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private Map<String, Object> mapValue(String file, String key) {
        Object value = value(file, key);
        return value instanceof Map<?, ?> map ? stringMap(map) : new LinkedHashMap<>();
    }

    private Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object element : collection) {
            result.add(String.valueOf(element));
        }
        return Set.copyOf(result);
    }

    private Double numberValue(Object value) {
        if (value instanceof Number number) {
            double result = number.doubleValue();
            return Double.isFinite(result) ? result : null;
        }
        return null;
    }

    private String boundsText(MechanicDefinition definition) {
        if (definition.minimum() == null && definition.maximum() == null) {
            return "";
        }
        return " (allowed " + definition.minimum() + ".." + definition.maximum() + ")";
    }

    private String nameForRule(MechanicRule rule) {
        return "config:" + rule.file() + ":" + rule.section() + ":" + rule.mechanicId() + ":" + rule.selector();
    }

    private String legacyId(String key) {
        return switch (key) {
            case "dropAtFeetRadius" -> "drop_at_feet_radius";
            case "experienceOrbRange" -> "experience_orb_range";
            case "furnaceCookingSpeed" -> "furnace_cooking_speed";
            case "itemDespawnTicks" -> "item_despawn_time";
            case "entityTickBudget" -> "entity_tick_budget";
            case "blockEntityTickBudget" -> "block_entity_tick_budget";
            case "entityTickInterval" -> "entity_tick_interval";
            case "monsterSpawnCapMultiplier" -> "monster_spawn_cap_multiplier";
            case "creatureSpawnCapMultiplier" -> "creature_spawn_cap_multiplier";
            case "ambientSpawnCapMultiplier" -> "ambient_spawn_cap_multiplier";
            case "waterCreatureSpawnCapMultiplier" -> "water_creature_spawn_cap_multiplier";
            case "waterAmbientSpawnCapMultiplier" -> "water_ambient_spawn_cap_multiplier";
            case "miscSpawnCapMultiplier" -> "misc_spawn_cap_multiplier";
            case "instantItemMerge" -> "item_merging_enabled";
            case "instantPickup" -> "item_pickup_enabled";
            case "disableEntityCollision" -> "entity_collision_enabled";
            case "hopperPullRange" -> "hopper_pull_range";
            case "hopperTransferCooldown" -> "hopper_cooldown";
            case "disableFireSpread" -> "fire_spread_enabled";
            case "disableLeafDecay" -> "leaf_decay_enabled";
            case "noXpSplit" -> "xp_splitting_enabled";
            case "noWallTargeting" -> "mob_targeting_enabled";
            case "projectileLifetime" -> "entity_activation_range";
            case "preventAnvilDamage" -> "anvil_behavior_enabled";
            case "filledShulkerBoxesStack" -> "item_merging_enabled";
            case "allowChestOpeningUnderBlocks" -> "container_interaction_enabled";
            case "dispenserDropAmount" -> "dispenser_behavior_enabled";
            case "crafterCraftAmount" -> "crafter_behavior_enabled";
            default -> null;
        };
    }

    private boolean legacyInverse(String key) {
        return switch (key) {
            case "disableEntityCollision", "disableFireSpread", "disableLeafDecay", "noXpSplit" -> true;
            default -> false;
        };
    }

    private static Yaml createYaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        DumperOptions dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setPrettyFlow(true);
        dumper.setIndent(2);
        dumper.setWidth(120);
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }
}
