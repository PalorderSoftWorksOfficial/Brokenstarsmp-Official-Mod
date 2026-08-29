# BrokenStarSMP Official Mod — AGENTS.md

## Project Overview
Server-side Fabric mod for Minecraft 26.2 (Java 25, Kotlin 2.3). Builds to a JAR via Gradle + Fabric Loom. Not a web application — no web server, no port 3000. The Base44 preview cannot display this project; verify via `docker compose -f docker-compose.base44.yml run --rm build`.

## Build
```bash
docker compose -f docker-compose.base44.yml run --rm build
```
Uses `eclipse-temurin:25-jdk`. Gradle wrapper is 9.5.1. Fabric Loom downloads Minecraft mappings on first build (network required, ~3 min).

## Architecture (current)
- **Entry point**: `DropAtFeet implements ModInitializer` — registers all callbacks, commands, and subsystems
- **Second entry point**: `GrimIntegration implements ModInitializer` — GrimAC flag → translation probe bridge
- **Config**: `ConfigManager` — annotation-driven YAML config with reflective field binding
- **Economy**: `EconomyExtras` (static singleton), `BanknoteUtil`/`BanknoteStore`, `PlayerVaultManager`, `SellWand` — depends on external `economycraft` mod
- **Translation probe**: `TranslationProbeController` — sends virtual signs to detect client mods via translation key probing
- **GrimAC**: `GrimIntegration` — `compileOnly` GrimAPI, runtime GrimAC nested as JAR
- **Modrinth**: `ModrinthPackageManager` — package manager for installing/updating mods (blocking HTTP)
- **Lua**: `LuaScriptManager` — sandboxed Lua execution via external processes
- **Messages**: `Messages` + `MiniMessageApi` — Adventure MiniMessage → native Component bridge
- **Mixins**: 25+ mixins for gameplay tweaks (anvils, furnaces, chests, hoppers, item merging, etc.)

## Audit Findings (Stage 1 — fixed)
1. **ConfigManager.loadConfig** — threw `IllegalStateException` on malformed YAML, crashing server startup → now logs and falls back to defaults
2. **PermissionUtil.isOwnerOrDev** — hardcoded username `"AdoreKittens"` (username can change, security risk) → switched to UUID with system-property override
3. **PlayerVaultManager** — serialized ALL vaults to JSON on every single mutation → added dirty flag with periodic flush (every 200 ticks) + full save on shutdown
4. **Messages.initialize** — threw `IllegalStateException` if `messages.yml` missing from jar → now logs warning and uses empty defaults
5. **AptCommand** — wrapped `IOException` in `RuntimeException` (ugly stack trace to players) → now sends user-friendly error message
6. **UnstableSMPFeatures.banAndKick** — injected player name into command string (injection risk) → now uses UUID + suppressed output
7. **DropAtFeet** — `new LuaScriptManager()` in `onInitialize()` could crash server startup if external process spawn fails → now caught and degraded
8. **PermissionUtil/TranslationProbeController** — silently swallowed exceptions (`catch ... ignored`) → now logged at debug level
9. **BanknoteStore** — `save()` returned `void`, hide persistence failure after marking redeemed → now returns `boolean` and logs critical warning on failure

## Audit Findings (Stage 1 — noted, not yet fixed)
- **ModrinthPackageManager**: All HTTP is synchronous on server thread via `AptCommand`. Should be async. (Stage 2+)
- **DropAtFeet.SCHEDULED_TASKS**: Plain `ArrayList`, not thread-safe. Currently server-thread-only but undocumented. (Stage 3)
- **Static global state**: EconomyExtras, ConfigManager, TranslationProbeController, Messages, GrimIntegration all use static singletons. (Stage 3 — service architecture)
- **TranslationProbeController.maybeRunAggregateCommands**: Config-defined command templates with `%player%` substitution — potential injection. (Stage 3)
- **PlayerVaultManager.save()**: Uses `server.registryAccess()` which may fail during abnormal shutdown. (Stage 3)
- **No automated tests**: Project has no test source set. (Stage 15)

## Supported Platforms
- **Fabric 26.2** (root project) — full mod with mixins, economy, translation probe, Lua scripting, GrimAC
- **Paper 26.2** (`paper/` subproject, `includeBuild`) — Paper plugin port using paperweight userdev, Mojang production mappings

## Removed Platforms (cleanup Aug 2026)
- `fabric-26.1.2/` — below 26.2, deleted
- `fabric-template/` — below 26.2, deleted
- `spigot/` — removed, replaced by Paper
- `quilt-1.21.11/` — below 26.2, deleted

## Paper Port (`paper/` subproject)
- Uses `io.papermc.paperweight.userdev` 2.0.0-beta.21 with dev bundle `26.2.build.+`
- Mojang production reobf configuration (Paper 1.20.5+ can load Mojang-mapped jars directly)
- Build: `docker compose -f docker-compose.base44.yml run --rm build ./gradlew -p paper build`
- Output: `paper/build/libs/brokenstarsmp-paper-2.0.0-26.2.jar`
- **Ported**: ConfigManager (annotation-driven YAML config), ServerRules/UnstableSMPRules, Messages/MiniMessageApi, ImmortalCommand, event listeners (fire spread, leaf decay, drop-at-feet, item merge, proximity messages, death ban, wither sound)
- **Not yet ported**: Economy (EconomyCraft dependency), Translation probe (GrimAC integration), Lua scripting, Modrinth package manager, Polymer/SGUI UI, remaining mixins (furnace speed, chest opening, hopper, dispenser, beehive, crafter, cobblestone ore, entity tick budget, mob AI, projectile lifetime, XP orb range/split)

## Key Dependencies
- `economycraft` (required at runtime — economy API)
- GrimAPI (compileOnly), GrimAC (nested JAR from Modrinth)
- Adventure Platform Fabric, MiniMessage, Gson serializer
- Polymer, SGUI (UI framework)
- LuckPerms (optional, compileOnly)
- SnakeYAML (config), LuaJava (sandboxed scripts)
