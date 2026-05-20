# MasuCraftFixes (Wolds branch)

Server-side Forge 1.18.2 mod providing fixes for the Wolds Vault Hunters server.

This is a trimmed-down variant of the MasuCraft `main` branch. It contains only the fixes Wolds needs - patron perks, transmog, command-block gating, crafting-tweaks compress fix, Iskallia-dev gating, room-pool tab completion, chain-miner cap, and the trowel/mob-container dupe fix are intentionally omitted.

## Build

Requires Java 17 (system default is Java 21, so use `JAVA_HOME="C:/Program Files/Java/jdk-17"` when building):
```bash
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-wolds-<version>.jar`.

## Package Structure

- `com.masuary.masucraftfixes` - main mod class, event handler, LuckPerms integration
- `com.masuary.masucraftfixes.mixin` - all mixins

## Mixins

All mixins registered in `src/main/resources/mixins.masucraftfixes.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `AngelExpertiseMixin` | `AngelExpertise` (The Vault) | Prevents angel expertise from stripping FTB `/fly` flight |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |

## LuckPerms Permissions

| Permission | Effect |
|---|---|
| `masucraftfixes.ignores_player_limit` | Holder bypasses the server player-count cap on login (login disconnect is skipped and player is tagged `ignores_player_limit`) |

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-3.20.3.6055.jar` / `the_vault-1.18.2-3.21.2.6474.jar` - The Vault mod (mixin targets)

Remote (via CurseMaven):
- FTB Essentials, Quark, AutoRegLib, LuckPerms
