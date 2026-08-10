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
- FTB Essentials `/fly` compatibility with Angel Blocks, while retaining the vault flight restriction.
- One-time Vault `v1_66` to build `6884` `v1_67` data migration with verified backups. On Wolds, snapshot IDs quarantined by Vault and history referenced by a renamed `the_vault_VaultSnapshots.dat.old` manifest are tested against the known build `6573`, transitional late-`v1_66`, expanded add-on, and native build `6884` layouts, including removed historical Wolds and Unobtainium fields. Old and current manifest references are merged without discarding either history generation. A file is replaced only after a bounded complete decode, native-schema normalization, and stable native `v1_67` read-back; unreadable referenced files remain unchanged and are quarantined so Vault skips them safely.
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
- The Vault `1.18.2-3.21.6.6884`
- LuckPerms API
- FTB Essentials
- Crafting Tweaks
- Quark and AutoRegLib
