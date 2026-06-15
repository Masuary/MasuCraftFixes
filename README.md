# MasuCraftFixes

Server-side Forge `1.18.2` fixes for the MasuCraft/Wolds Vault Hunters server.

This branch is the Wolds-focused build. It keeps the fixes needed by this server pack and intentionally omits unrelated fixes from the broader MasuCraft branch.

## Build

Requires Java 17.

```bash
./gradlew build
```

Output:

```text
build/libs/masucraftfixes-wolds-<version>.jar
```

Current Gradle artifact name:

```text
masucraftfixes-wolds
```

## Main Features

- Vessel anti-AFK safeguards.
- LuckPerms player-limit bypass tag.
- Vault flight prevention for FTB Essentials flight.
- Optional CasinoCraft mixins, only applied when CasinoCraft is loaded.

## Other Commands

```text
/vesselantiafk debug true|false
```

## Important Files

- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixes.java`
- `src/main/java/com/masuary/masucraftfixes/EventHandler.java`
- `src/main/java/com/masuary/masucraftfixes/VesselAntiAfk.java`
- `src/main/java/com/masuary/masucraftfixes/VesselAntiAfkCommand.java`
- `src/main/resources/mixins.masucraftfixes.json`

## Dependencies

Local JARs are expected in `deps/`.

Important compile targets:

- Forge `1.18.2-40.3.11`
- The Vault `1.18.2-3.21.5.6573`
- LuckPerms API
- FTB Essentials
- Crafting Tweaks
- Quark and AutoRegLib
