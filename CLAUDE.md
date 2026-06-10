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

- `com.masuary.masucraftfixes` - main mod class, event handler, LuckPerms integration, Vessel anti-AFK
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
| `AngelExpertiseMixin` | `AngelExpertise` (The Vault) | Prevents angel expertise from stripping FTB `/fly` flight |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |
| `VaultListenerSyncReplaceMixin` | `Listener` (The Vault 3.21.5.6573) | Replaces most per-tick active-vault FULL syncs with HUD-root DIFF syncs, keeps forced/periodic FULL baselines, and skips stale offworld listener sync |
| `VaultMessageSyncTelemetryMixin` | `VaultMessage.Sync` (The Vault) | Records vault sync payload bytes, construction time, and FULL/HUD_DIFF reason summaries |

## LuckPerms Permissions

| Permission | Effect |
|---|---|
| `masucraftfixes.ignores_player_limit` | Holder bypasses the server player-count cap on login (login disconnect is skipped and player is tagged `ignores_player_limit`) |

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-3.20.3.6055.jar` / `the_vault-1.18.2-3.21.2.6474.jar` / `the_vault-1.18.2-3.21.5.6573.jar` - The Vault mod (mixin targets; Wolds sync mitigation compiles against 3.21.5.6573)

Remote (via CurseMaven):
- FTB Essentials, Quark, AutoRegLib, LuckPerms
