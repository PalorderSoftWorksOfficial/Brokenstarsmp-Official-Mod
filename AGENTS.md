# BrokenStarSMP Official Mod

## Project Overview
Server-side Minecraft project organized as a Gradle multi-project build. The primary implementation targets Fabric 26.2 with Java 25 and Kotlin 2.3. Platform modules are separated so additional loaders and server platforms can be implemented without keeping independent Gradle projects.

## Build
```bash
./gradlew build
```

The repository also supports the existing Base44 container build:
```bash
docker compose -f docker-compose.base44.yml run --rm build
```

All module JAR tasks publish directly into the root `build/libs/` directory.

## Module Layout

- `common` — platform-independent library code.
- `fabric` — current Fabric implementation for Minecraft 26.2.
- `fabric-26.1.2` — legacy Fabric implementation.
- `paper` — Paper API platform module.
- `spigot` — Spigot API platform module.
- `velocity` — Velocity proxy platform module.
- `quilt` — Quilt platform module.

Platform modules use the root `settings.gradle`, `build.gradle`, and `gradle.properties`. Platform-specific dependencies and source sets belong in their corresponding module.

## Fabric 26.2 Architecture

- **Entry point**: `DropAtFeet` registers callbacks, commands, and subsystems.
- **Second entry point**: `GrimIntegration` bridges GrimAC flags to the translation probe.
- **Config**: `ConfigManager` provides annotation-driven YAML configuration.
- **Economy**: `EconomyExtras`, banknotes, player vaults, and sell wand features integrate with `economycraft`.
- **Translation probe**: `TranslationProbeController` detects client modifications through translation-key probing.
- **GrimAC**: GrimAPI is compile-only and the Fabric runtime JAR is nested into the Fabric distribution.
- **Modrinth**: `ModrinthPackageManager` installs and updates mod packages.
- **Lua**: `LuaScriptManager` provides sandboxed Lua execution.
- **Messages**: `Messages` and `MiniMessageApi` provide Adventure-based message formatting.
- **Mixins**: gameplay behavior changes remain isolated in the Fabric module.

## Platform Rules

Do not add platform-specific Minecraft, Fabric, Bukkit, Paper, Spigot, Velocity, or Quilt imports to `common`.

Keep loader-specific entrypoints, mixins, metadata, and platform dependencies inside the corresponding platform module.

When adding a new platform, create a top-level module, add it to `settings.gradle`, configure its platform dependencies, and keep its generated JAR in root `build/libs/`.

## Key Dependencies

- `economycraft` runtime integration
- GrimAPI compile-only integration and nested GrimAC runtime for Fabric
- Adventure Platform Fabric and Adventure serializers
- Polymer and SGUI
- LuckPerms optional compile-only integration
- SnakeYAML
- LuaJava
