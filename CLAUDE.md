# MasuCraftFixes (Wolds branch)

Server-side Forge 1.18.2 mod providing fixes for the Wolds Vault Hunters server.

This is a trimmed-down variant of the MasuCraft `main` branch. It contains only the fixes Wolds needs - patron perks, transmog, command-block gating, crafting-tweaks compress fix, Iskallia-dev gating, room-pool tab completion, chain-miner cap, and the trowel/mob-container dupe fix are intentionally omitted.

## Build

Requires Java 17 (system default is Java 21, so set `JAVA_HOME` when building):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build
```
`gradlew` here has CRLF line endings, so if the shell refuses to run it, launch the wrapper
through its jar instead:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17-openjdk/bin/java \
  -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build
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
| `VaultChallengeTickGuardMixin` | `ChallengeManager` (The Vault) | Prevents stale server-tick callbacks from accessing unloaded, replaced, detached, or deletion-marked worlds |

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
callback ran. Since 1.8.0 `VaultChallengeGuardMonitor.warnIfGuardDisarmed` also emits a
main-log WARN naming both versions when the installed Vault build is not
`VaultChallengeTickGuard.TARGET_VAULT_VERSION`, so a Vault update cannot disarm the guard
with only a status string inside the guard's own file. On the live network the guard blocks
stale ticks daily, so that silence was the real risk. A required TAIL injection into base `ChallengeManager.onDetach`
emits DETACHED only for previously blocked managers and clears diagnostic state.
It proves base listener cleanup, not completion of subclass cleanup or disk saves.
The existing main-log stale-world warning is retained. No active world references
are retained by diagnostics. See README for event meanings and the monitoring command.
Tests exercise real file output, flushing, reopen/append, rotation, creation
failure, newline escaping, counters, throttling, and exact detach target metadata.
Test-only `log4j2-test.xml` avoids initializing Forge's terminal/file appenders
inside the noninteractive test worker; it is not packaged in the mod JAR.
These checks do not replace verification on the affected dedicated server.

### Legacy Vault snapshot migration (retired in 1.8.0)

The one-time v1_66 to v1_67 migration, its five array/float-list safety mixins, the
`VaultSnapshotV166CompatibilityMixin`, and the `vaultDataCompatibility` config section were removed in
1.8.0. The key `enableLegacy6573V166Compatibility` was `false` on all six live backends, so the code
had been inert in production; the Sky line retired the same machinery in its 1.7.0. The mod no longer
registers a COMMON config spec at all, so an existing `config/masucraftfixes-common.toml` is simply
left orphaned rather than rewritten. See `RELEASE_1.8.0.md`, and the git history before 1.8.0 if the
migration ever has to be revived.

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

<!-- masuary-os-area --> Masuary OS area: [minecraft](/mnt/data/AI/os/areas/minecraft.md) (where this project sits in the map; update that line when this folder moves)
