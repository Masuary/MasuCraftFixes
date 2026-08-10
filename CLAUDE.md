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

- `com.masuary.masucraftfixes` - main mod class, configuration, Vault compatibility, event handler, LuckPerms integration, Vessel anti-AFK
- `com.masuary.masucraftfixes.mixin` - all mixins

## Event Handlers

| Class | Purpose |
|---|---|
| `EventHandler` | Player-limit bypass (`ignores_player_limit` tag) on login |
| `VesselAntiAfk` | Greed Vessel anti-cheese, three behaviors: **(1) Pull timer** - stamps engagement on Vessel taking/dealing damage, target/Vessel moving >3 blocks, or `target.hurtTime > 0` (catches no-entity damage sources like Soul Tether). After `TIMEOUT_TICKS` of no engagement with target >12 blocks away, teleports player onto the Vessel. **(2) Lost-target pull** - tracks last target UUID; if Vessel goes `LOST_TARGET_TIMEOUT_TICKS` without any target, looks up the last player by UUID in the server's player list and yanks them back to the Vessel (same-dim only). **(3) Out-of-world rescue** - captures Vessel spawn position on `EntityJoinWorldEvent`; if Vessel later takes `OUT_OF_WORLD` damage (fell out of arena), cancels the damage via `LivingAttackEvent`, teleports it back to the anchor, zeros velocity/fallDistance. Anchor preserved across chunk reload, dropped only on KILLED/DISCARDED. **Teleport-jump filter** (`MAX_LEGIT_MOVE_SQ = 100`): per-tick movement >10 blocks is treated as a teleport and silently rebaselines without stamping (kills the void-respawn-mod spam-stamp exploit). Class detection via class-hierarchy name walk to handle subclasses without geckolib transitive dep. `DEBUG = true` flag emits verbose `[VesselAntiAfk DEBUG]` traces. |

## Mixins

All mixins registered in `src/main/resources/mixins.masucraftfixes.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `AngelExpertiseMixin` | `AngelExpertise` (The Vault) | Prevents angel expertise from stripping FTB `/fly` flight outside vaults while preserving the operator bypass |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |
| `VaultSnapshotV166CompatibilityMixin` | `VaultSnapshot` (The Vault) | Decodes build 6573 snapshots during the one-time v1_67 disk migration |

The three FTB Essentials flight mixins are applied only when `ftbessentials` is loaded. They use FTB's persisted `fly` flag as the flight-ownership signal and leave other flight providers to their own compatibility logic.

The v1_66 compatibility reader is restricted to The Vault
`1.18.2-3.21.6.6884`. Before normal world loading, MasuCraftFixes atomically
converts active Vault records and historical snapshots from build 6573's
ambiguous v1_66 schema to the standard v1_67 schema. Original files are kept
under `world/data/masucraftfixes-v166-backup`, and completion is recorded in
`world/data/masucraftfixes-v166-to-v167.properties`. After conversion, the
native v1_67 schema is restored before Vault loads the world normally. Wolds
contains snapshots from multiple schema generations that share the v1_66
version number, so only IDs already present in Vault's persisted corruption
quarantine are passed through the build 6573 reader. Native-compatible v1_66
snapshot files remain unchanged. Successfully migrated IDs are removed from the
quarantine, while files that still cannot be decoded remain preserved and
quarantined. Unexpected NBT, backup, write, and verification failures still
stop startup. Vault's `the_vault_VaultSnapshots.dat` manifest and segmented
snapshot files use uncompressed NBT, unlike standard compressed Forge
`SavedData` files.

## LuckPerms Permissions

| Permission | Effect |
|---|---|
| `masucraftfixes.ignores_player_limit` | Holder bypasses the server player-count cap on login (login disconnect is skipped and player is tagged `ignores_player_limit`) |

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-3.21.6.6884.jar` - The Vault mod (mixin targets)

Remote (via CurseMaven):
- FTB Essentials, Quark, AutoRegLib, LuckPerms
