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
- Server-only guard against challenge ticks accessing unloaded or deletion-marked vault worlds on VH build `6884`. Native VH cleanup handles stale challenge records; no world-data wipe is required.
- LuckPerms player-limit bypass tag.
- FTB Essentials `/fly` compatibility with Angel Blocks, while retaining the vault flight restriction.
- One-time Vault `v1_66` to build `6884` `v1_67` data migration with verified backups. On Wolds, snapshot IDs quarantined by Vault and history referenced by a renamed `the_vault_VaultSnapshots.dat.old` manifest are tested against the known build `6573`, transitional late-`v1_66`, expanded add-on, and native build `6884` layouts, including removed historical Wolds and Unobtainium fields. Old and current manifest references are merged without discarding either history generation. A file is replaced only after a bounded complete decode, native-schema normalization, and stable native `v1_67` read-back; unreadable referenced files remain unchanged and are quarantined so Vault skips them safely.
- Optional CasinoCraft mixins, only applied when CasinoCraft is loaded.

## Other Commands

```text
/vesselantiafk debug true|false
```

## Monitoring the Vault challenge guard

Version `1.7.12` creates `logs/masucraftfixes-vault-challenge-guard.log` on server
startup. No configuration or client update is required. From the server folder:

```bash
tail -F logs/masucraftfixes-vault-challenge-guard.log
```

- `START state=ENABLED`: the installed VH version matches the guard's target.
  This is startup confirmation, not proof a challenge callback has run.
- `CALLBACK_OBSERVED`: the injected guard actually ran for the first time this session.
- `BLOCKED`: a callback was canceled. Includes challenge UUID/type, dimension,
  position, and the deletion/attachment/world-registration flags explaining why.
- `DETACHED`: VH's base `onDetach` completed for a previously blocked challenge.
  Match the challenge UUID with `BLOCKED`. This confirms listener cleanup, not
  completion of every subclass's cleanup or successful saving of the world.
- `SUMMARY`: emitted once per minute while the server ticks, even with no
  challenges. Shows cumulative checked/allowed/blocked callback counts, blocked
  challenge count, and detached-blocked challenge count since server start.
- `STOP`: final session totals during normal shutdown.

`blockedCallbacks=0` means no unsafe callbacks were observed, not that the fix is
broken. `callbackObserved=false` means no callback has exercised the guard yet.
`DISABLED_VERSION_MISMATCH` means the installed VH version is unsupported.
Repeatedly increasing blocked counts without detachment warrant investigation;
the guard does not claim each canceled callback would have caused a crash.

The log appends across restarts, rotates at 5 MB, and keeps three numbered
archives (`.1` is newest), approximately 20 MB total. Individual details are
limited to 100 per minute; counters still include every event and summaries
report `detailsSuppressedSinceSummary`. Normal callbacks only update counters.
Logging failures are reported in the main server log and disable the dedicated
writer until restart without changing guard behavior.

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
