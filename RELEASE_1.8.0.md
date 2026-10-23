# MasuCraftFixes Wolds 1.8.0

Decided 2026-10-23 after comparing this line against the Sky line (`MasuCraftFixes`, 1.8.0 on branch
`remove-v166-compat`). The two share only 7 source files and all 7 differ, so the comparison was done
per feature rather than as a merge.

Scope: retire the `v1_66` machinery, harden the Vault challenge guard's failure mode, and roll the
guard out to the five backends that do not have it. Nothing is ported from the Sky line.

## Live state this was decided against

Six Forge backends, two panels. US Myth and Zest on the Wolds host panel; EU IRIS, NOVA, VOID and Hub
on `panel.masucraft.eu`. Installed `masucraftfixes-wolds` per backend:

| Backend | Version | Challenge guard |
|---|---|---|
| EU IRIS | 1.7.12 | active |
| US Myth | 1.7.10 | absent |
| EU NOVA | 1.7.10 | absent |
| EU VOID | 1.7.10 | absent |
| EU Hub | 1.7.8 | absent |
| US Zest | 1.6.3 | absent |

Zest's 1.7.x jars are present but renamed `.jar.old`, so it has been running six releases behind the
rest. 1.8.0 is a 1.6.3 -> 1.8.0 jump there, and it brings more than the guard: comparing the live
1.6.3 jar with 1.8.0, Zest also gains four CasinoCraft blackjack audit handlers (`CasinoCraftMessage`
Player/Score/Start/State `ServerHandlerMixin`) and `FtbEssentialsFlightCompatibility`. The Hub (1.7.8)
gains the guard and loses only `VaultSnapshotV166CompatibilityMixin`; Myth, NOVA and VOID (1.7.10) gain
the guard and lose the six v1_66 mixins; IRIS (1.7.12) gains only the mismatch warning.

## 1. Retire the Vault `v1_66` migration

Remove, with the config key:

- `VaultV166Compatibility`, `VaultV166DiskMigration`
- `mixin/VaultSnapshotV166CompatibilityMixin`
- `mixin/ArrayAdapterMigrationSafetyMixin`, `ByteArrayAdapterMigrationSafetyMixin`,
  `IntArrayAdapterMigrationSafetyMixin`, `LongArrayAdapterMigrationSafetyMixin`,
  `WoldsFloatListAdapterMigrationSafetyMixin`
- the five matching entries in `mixins.masucraftfixes.json`
- `MasuCraftFixesConfig.ENABLE_LEGACY_V166_VAULT_DATA_COMPATIBILITY` and its
  `enableLegacy6573V166Compatibility` key

**Why it is safe:** the key is `false` in `config/masucraftfixes-common.toml` on **all six** backends
(read 2026-10-23), and Myth logs `Legacy Vault v1_66 disk compatibility is disabled` at boot. The code
default is `true`, so this was a deliberate operator decision on every server. The Sky line retired the
same machinery in its 1.7.0. Runtime behaviour after removal is identical to today.

**Operational note (corrected during implementation):** the config class held nothing but this key, so
`MasuCraftFixesConfig` and the `registerConfig(ModConfig.Type.COMMON, ...)` call are gone too. The mod
no longer registers a COMMON spec at all, which means Forge does not touch
`config/masucraftfixes-common.toml`: wolf's six existing files are left orphaned on disk rather than
rewritten. Harmless, and they can be deleted with the other cleanup. The earlier note in this spec said
Forge would rewrite them, which was only true if an empty spec stayed registered.

## 2. Keep the Vault challenge guard, and make its failure loud

Keep `VaultChallengeTickGuard`, `mixin/VaultChallengeTickGuardMixin`,
`VaultChallengeGuardDiagnostics`, `VaultChallengeGuardLog` and `VaultChallengeGuardMonitor` unchanged.

**Why it stays:** it is doing live work, not idling. On IRIS, `logs/masucraftfixes-vault-challenge-guard.log`
shows `state=ENABLED` with blocks every day since 2026-10-17: 84, 54, 13, 3, 3, 10 and **9 on
2026-10-23**, on real challenge types (`EliteChallengeManager`, `XMarkChallengeManager`) in real vault
dimensions, each with `challengeDeleted=false attachedWorldMatches=true` -- challenges that believed
they were healthy while their vault world was unregistered or deletion-marked. The 1.7.12 commit noted
"dedicated-server confirmation remains pending"; this is that confirmation.

**Change:** `VaultChallengeGuardDiagnostics` arms itself only when the running VH equals
`VaultChallengeTickGuard.TARGET_VAULT_VERSION` (`1.18.2-3.21.6.6884`) and otherwise reports
`DISABLED_VERSION_MISMATCH` as a status string. Replace that with a startup `WARN` naming both
versions and what to do:

```
WARN  Vault challenge guard DISABLED: built for 1.18.2-3.21.6.6884, running <actual>.
      Re-verify the synthetic tick lambda target and re-pin TARGET_VAULT_VERSION before relying on it.
```

A pack update currently removes demonstrably-active protection with only a status string to notice it
by. This is the same trap class as the reset fingerprint, and gets the same loud failure. Add it to the
update checklist next to `reset-artifact-preflight.py`.

**Rollout:** 1.8.0 arms the guard for the first time on Myth, Zest, NOVA, VOID and Hub. Myth, Zest,
NOVA and VOID are the vault-running backends, so this is the main functional gain of the release: the
condition IRIS logs daily is currently happening unguarded there. The Hub hosts no vaults, so the guard
is inert there.

## 3. VesselAntiAfk: keep, and hunt it during QA

Keep `VesselAntiAfk` and `VesselAntiAfkCommand` in 1.8.0. It is the Greed-boss anti-exploit: it anchors
the Vessel's arena spawn on `EntityJoinWorldEvent`, persists the anchor in entity NBT, and cancels
`outOfWorld` damage while teleporting the Vessel back, so a player cannot AFK while a glitched Vessel
dies below Y=-64.

It was reported as "not working great". Retiring it reopens the exploit, so the QA pass below must
include a deliberate attempt to break it (`/vesselantiafk debug true` for tracing):

- glitch a Vessel out of the arena and confirm it is returned rather than lost
- confirm the anchor survives a server restart, which is what commit `8b52b00` added
- confirm a Vessel already in the world when the mod loaded is handled (the no-anchor path cancels
  damage and strands it deliberately)
- look for a path that still completes the AFK exploit

Decide retire-versus-fix from what QA finds. If it has to be retired, write the exploit up for wolf
rather than removing it silently. The Vessel is Vault Hunters' own boss (`the_vault:the_vessel`,
`iskallia.vault.entity.boss.TheVesselEntity`), so the arena is not wolf's code; his mod is still a
reasonable home for a durable fix because it already mixes into the Vessel
(`MixinGreedVesselEntity`). (Corrected 2026-10-23: an earlier version said the arena bounds live in
`wolds-vaults-official-mod`.)

### Console-stage results (2026-10-23, IRIS test copy, 1.8.0, Forge 40.3.11)

Driven from the server console with `/vesselantiafk debug true`; no client needed.

| Test | Result |
|---|---|
| Void rescue: summoned on an arena platform, teleported to y=-140 | **pass**: "fell out of arena (Y=-140.0), rescued back to (10000.5,101.0,10000.5) and damage cancelled" |
| Restart persistence: moved 7 blocks off its anchor, saved, full restart, voided from the new spot | **pass**: "restored from NBT: anchor=(10000.5,101.0,10000.5)", then rescued to the *original* anchor, not where it stood at restart |
| Cleanup | **pass**: on death, "left world permanently (reason=KILLED, all state dropped)", so the tracking maps do not leak |
| Environmental damage: lava, fire (`Fire:400s`), buried in stone | **immune**: health stayed `1.0E7`, and no damage event fired, so nothing stamps engagement |

What the bytecode of `TheVesselEntity` on VH 6884 explains:

- `hurt()` ignores everything except player attacks (25% dodge while it is not attacking) and
  `DamageSource.OUT_OF_WORLD`. **The void is the only passive way a Vessel can die**, which is exactly
  the path VesselAntiAfk closes. An environmental trap cannot kill it, and cannot reset the pull timer
  either, because ignored damage never reaches Forge's damage events. The suspected exploit (a trap
  that keeps "engagement" fresh while the player AFKs) does not exist.
- `kill()` is overridden to `setHealth(1.0F)`: `/kill` reports "Killed The Vessel" but leaves it on
  1 HP, independent of VesselAntiAfk. **To remove a stuck Vessel, an admin uses
  `/data merge entity <vessel> {Health:0f}`** (verified: it dies and VesselAntiAfk drops its state).
- Health is `1.0E7` on a bare summon; a real Greed fight sets it from `GreedConfig$VesselConfig`.

**Still to test, and it needs a CMA client:** the player-side behaviour, which is where "not working
great" most likely lives because it is the part players notice: the pull timer (`TIMEOUT_TICKS` 6000,
target more than 12 blocks away teleports the player onto the Vessel), the lost-target pull
(`LOST_TARGET_TIMEOUT_TICKS` 2400), the teleport-jump filter (`MAX_LEGIT_MOVE_SQ`), and the Soul Tether
engagement path (`target.hurtTime > 0`). Candidate weak spots to probe: a player standing just inside
the 12-block radius out of the Vessel's reach (the pull never fires), and whether a real Greed-vault
fight behaves like a bare summon at all.

### Player-side results (2026-10-23, IRIS test copy with the full Wolds drop, CMA client, bare summon)

Masuary in survival with Resistance 255 against a summoned Vessel in a fenced bedrock arena at y=200;
trace in `/mnt/data/AI/work/wolds-vessel-hunt-2026-10-23/vessel-antiafk-trace.log`.

| Test | Result |
|---|---|
| Lost-target pull: player 36 blocks away, the Vessel drops the target | **pass**: "Pulled Masuary back to Vessel ... after 120s without target", onto the Vessel |
| Void rescue on a real fall: the Vessel jumped its 2-block fence toward the player and fell | **pass**, twice: "fell out of arena (Y=-129.1 / -131.4), rescued back to (20003.5,200.0,20003.5)" |
| Teleport filter | **pass**: player and Vessel jumps of 330-400 blocks logged as "teleport filtered, no stamp" |
| Player 17-23 blocks away, out of melee reach | the Vessel hits at range (0 damage under Resistance), every hit stamps engagement: a real fight, no pull needed |
| Player sealed in a bedrock box 9.6 blocks away | the Vessel still dealt damage into the box and the player ended up outside it, falling into the void (cause is VH's; no pull was logged). Inside 12 blocks the timer is gated by design |
| **The 5-minute pull (`TIMEOUT_TICKS`)** | **never fires while the Vessel has a target.** Every timer snapshot showed `sinceEngagement=0s`, at every distance up to 75 blocks |

Why the 5-minute pull is dead: VH's targeting re-calls `setTarget` with the same player several times a
second (15 `acquired target` events in 1.6 s at 20:43:59, same player, same spot, before the first hit at
20:44:19), Forge fires `LivingChangeTargetEvent` each time, and `onTargetChanged` stamps
`lastEngagementTick` on every one. So only the lost-target pull (120 s without a target) ever moves a
player, and a player who keeps the Vessel's target lock without fighting is never pulled. That is the
most likely meaning of "not working great".

Not tested: a real Greed-vault fight (VH's Greed config sets health and may change targeting), and Soul
Tether. Neither is needed for the decision below.

Decision for the owner: **fix in 1.8.1** (recommended): stamp in `onTargetChanged` only when the new
target differs from `Mob.getTarget()`, so the 5-minute timer measures hits and movement as intended,
then repeat this run. Retiring the class instead would also drop the two parts that demonstrably work
(void rescue, lost-target pull).

## 4. Nothing ported from the Sky line

| Sky item | Decision |
|---|---|
| `AbandonedVaults`, `PlayerListMixin` (`rescueFromDeadVault`), `deleteUnregisteredWorldFolders` | **Not wanted.** Wolf does not want the abandoned-vault feature. Independently, its abandonment signal is wrong here: it reads `world/playerdata/<uuid>.dat` mtime, which on a PlayerDataSync network only says when a player last visited *that* backend, so a player running vaults on NOVA and then playing IRIS for 7 days would have a live vault closed. All its VH APIs do exist on 6884 with identical signatures, so this is a product decision, not a technical block. |
| `mixin/PickarangMixin` | **Never port.** `MasuCrates-Wolds` already carries the same fix, and both `@Redirect` the same instruction in `Pickarang.tick`. Two mods redirecting one instruction is a hard Mixin failure at load. |
| `PatreonPerksHandler`, `RewardMixin`, `RewardPermissions`, `LpGrantedModelsData`, `IskalliaDevsMixin` | Not applicable. MasuCraft patron and reward system; Wolds has no equivalent. |
| `mixin/OrechidIgnemMixin` | Out. Wolds does ship Botania 435, but allowing Orechid Ignem in Nether biomes is a balance change on wolf's pack, not a fix. |
| `mixin/CommandBlockEditMixin` | Out. Needs Sky's `LuckPermsInt.canEditCommandBlocks` and a `masucraftfixes.commandblock.edit` node; changes who can edit command blocks on wolf's servers. |
| `LuckPermsInt` hardening (`isRemoved()` guard, `Optional` path) | Out. Not a live crash here: the only Wolds caller is `onPlayerLoggedIn`, which already guards `isAlive()` and `ModList.isLoaded("luckperms")`. Sky reached it through `PatreonPerksHandler`. |
| `mixin/ModifyCrystalSuggestionsMixin` | Out of this release. Harmless tab-completion QoL, deferred to keep 1.8.0 reviewable as a removal plus one warning. |

Also noted: the Sky repo has its own stale `wolds` branch (`83c16b1`, six behind). It is not this line.
The live Wolds code is this repo.

## 5. Version and release

`build.gradle` `version` 1.7.12 -> **1.8.0**, jar `masucraftfixes-wolds-1.8.0.jar`.

1.8.0 rather than 1.7.13 by owner decision: it marks the end of the v1_66 era on both lines at the same
number. It does **not** mean parity with Sky 1.8.0 -- this line keeps CasinoCraft, Vessel anti-AFK and
the challenge guard, and does not have Sky's abandoned-vault work or patron system.

## 6. Finish line

1. Removal and the guard warning implemented, `./gradlew build` clean with zero warnings.
2. Existing `src/test` checks still pass, including the four challenge-guard test classes
   (`VaultChallengeTickTargetTest` validates the synthetic lambda target by bytecode).
3. **Local testnet QA** on the Wolds copy at `/mnt/data/Minecraft/Servers/Wolds` via `tools/testnet`:
   clean boot with the five v1_66 mixins gone, guard reports `START state=ENABLED`, and the Vessel
   exploit hunt in section 3.
4. `masucraftfixes-wolds-1.8.0.jar` replaces 1.7.12 in the drop folder
   `/mnt/data/AI/work/wolds-network-prep-2026-10-23/Wolds-mods-2026-10-23/`, with `SHA256SUMS.txt`,
   `REMOVE.txt` and `INSTALL.md` updated. There is one send to wolf, so the removal only reaches the
   servers if it is in that folder.
5. `README.md` and `CLAUDE.md` updated: drop the v1_66 feature bullet and its monitoring prose, note
   the guard's new mismatch warning.

## Documents this updates

- **`README.md`** feature list: the `v1_66` bullet (line 33) goes; the challenge-guard bullet (line 30)
  gains the mismatch warning. The "Monitoring the Vault challenge guard" section stays and should say
  1.8.0.
- **`CLAUDE.md`**: the 1.7.12 guard section stays, plus the new warning. Remove v1_66 references.
- **Drop folder `INSTALL.md` / `REMOVE.txt`**: masucraftfixes line moves 1.7.12 -> 1.8.0, and the
  per-server remove lists already cover the older copies.
- No existing planning document is superseded. The readiness audit
  `../MasuCraft-CrossServer-wolds/audits/player-reset-wolds-update-readiness.md` is unrelated and
  unaffected.
