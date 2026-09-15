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
| `ArrayAdapterMigrationSafetyMixin` | `ArrayAdapter` (The Vault) | Bounds object-array allocation while legacy snapshots are being migrated |
| `ByteArrayAdapterMigrationSafetyMixin` | `ByteArrayAdapter` (The Vault) | Bounds byte-array allocation while legacy snapshots are being migrated |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `IntArrayAdapterMigrationSafetyMixin` | `IntArrayAdapter` (The Vault) | Bounds integer-array allocation while legacy snapshots are being migrated |
| `LongArrayAdapterMigrationSafetyMixin` | `LongArrayAdapter` (The Vault) | Bounds long-array allocation while legacy snapshots are being migrated |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |
| `VaultChallengeTickGuardMixin` | `ChallengeManager` (The Vault) | Prevents stale server-tick callbacks from accessing unloaded, replaced, detached, or deletion-marked worlds |
| `VaultSnapshotV166CompatibilityMixin` | `VaultSnapshot` (The Vault) | Promotes fully decoded legacy snapshots during the one-time v1_67 disk migration |
| `WoldsFloatListAdapterMigrationSafetyMixin` | `ElixirBreakpointMap.FloatListAdapter` (Wolds Vaults) | Bounds elixir float-list allocation while legacy snapshots are being migrated |

The three FTB Essentials flight mixins are applied only when `ftbessentials` is loaded. They use FTB's persisted `fly` flag as the flight-ownership signal and leave other flight providers to their own compatibility logic.

### Vault challenge unload guard (1.7.11)

The server-only challenge guard is pinned to The Vault `1.18.2-3.21.6.6884`.
It injects into `ChallengeManager.lambda$registerEvents$3`, before the virtual
`onTick` call reaches elite or other challenge subclasses. World deletion can
close chunk storage earlier in the same tick, before native `ChallengeData`
cleanup detaches the challenge. The guard requires an undeleted challenge,
the same attached and registered world instance, and no virtual-world deletion
flag. Invalid callbacks are canceled so native cleanup can finish normally.
A warning records the challenge UUID, position, and dimension once per stale
manager. The guard does not use active-vault membership, change animation
serialization, alter world deletion, or directly edit any saved data.
Clients and Velocity plugins do not need this update.

`./gradlew test` covers the lifecycle policy, including world replacement and
reattachment, and verifies the synthetic callback, registration, and shadow
fields against the exact Vault JAR without loading Minecraft classes. Javac
hides synthetic methods, so only the injector's target warning is suppressed;
the bytecode test and required runtime injection enforce the target instead.
Dedicated-server reproduction remains a separate validation gate.

### Challenge guard diagnostics (1.7.12)

`VaultChallengeGuardMonitor` owns server-thread-only session counters and the
dedicated `logs/masucraftfixes-vault-challenge-guard.log`. Forge server-start,
server-tick END, and server-stopped events initialize, summarize, and close it.
`VaultChallengeGuardDiagnostics` keeps policy/counters independent of Minecraft
for tests. `VaultChallengeGuardLog` owns an isolated Log4j rolling appender using
Forge's existing Log4j dependency, without attaching to or changing root logging.
Writes flush immediately. Append mode preserves prior boots; 5 MB rotation plus
three archives bounds retention. Up to 100 BLOCKED/DETACHED details are emitted
per one-minute summary interval; total counters never depend on that limit.
I/O failures disable diagnostics until restart with a contextual main-log error,
but must not change the challenge guard's decision.

START reports version eligibility, while CALLBACK_OBSERVED proves the injected
callback ran. A required TAIL injection into base `ChallengeManager.onDetach`
emits DETACHED only for previously blocked managers and clears diagnostic state.
It proves base listener cleanup, not completion of subclass cleanup or disk saves.
The existing main-log stale-world warning is retained. No active world references
are retained by diagnostics. See README for event meanings and the monitoring command.
Tests exercise real file output, flushing, reopen/append, rotation, creation
failure, newline escaping, counters, throttling, and exact detach target metadata.
Test-only `log4j2-test.xml` avoids initializing Forge's terminal/file appenders
inside the noninteractive test worker; it is not packaged in the mod JAR.
These checks do not replace verification on the affected dedicated server.

### Legacy Vault snapshot migration

The v1_66 compatibility reader is restricted to The Vault
`1.18.2-3.21.6.6884`. Before normal world loading, MasuCraftFixes atomically
converts active Vault records and historical snapshots from build 6573's
ambiguous v1_66 schema to the standard v1_67 schema. Original files are kept
under `world/data/masucraftfixes-v166-backup`, and completion is recorded in
`world/data/masucraftfixes-v166-to-v167.properties`. After conversion, the
native v1_67 schema is restored before Vault loads the world normally. Wolds
contains snapshots from multiple schema generations that share the v1_66
version number, so IDs already present in Vault's persisted corruption
quarantine and IDs referenced by a renamed
`the_vault_VaultSnapshots.dat.old` manifest are tested against the build 6573,
transitional late-v1_66, expanded add-on, and native build 6884 layouts. Old
references are prepended to the regenerated manifest while every current
reference remains authoritative later in the list. Recovery temporarily restores
historical Wolds objective suppliers and Unobtainium barrel/chest statistic
fields that newer add-on builds removed, plus v1_67-only fields registered by
server add-ons in the affected nested Vault data types. Each successful
candidate must consume the complete source bit stream, be normalized through
the native v1_67 registries, produce an unambiguous result, and reach a stable
native v1_67 decode/re-encode fixed point before its original is backed up and
atomically replaced.

Native-compatible, unreferenced v1_66 snapshot files remain unchanged.
Successfully migrated IDs are removed from quarantine, while referenced files
that cannot be decoded, are missing, or have ambiguous results remain preserved
and quarantined. Migration-only bounds on Vault primitive/object arrays and the
Wolds elixir float-list adapter prevent malformed schema candidates from
exhausting the heap without changing normal runtime decoding. Marker format 6
makes servers that ran an earlier migration import and recover the old manifest
once, then makes later boots idempotent. Unexpected NBT,
backup, write, and verification failures still stop startup. Vault's
`the_vault_VaultSnapshots.dat` manifest and segmented snapshot files use
uncompressed NBT, unlike standard compressed Forge `SavedData` files.

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
