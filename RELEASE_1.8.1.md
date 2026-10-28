# MasuCraftFixes-wolds 1.8.1

One change: `VesselAntiAfk` and `VesselAntiAfkCommand` are gone (owner decision, 2026-10-28). Nothing else
differs from 1.8.0; the jar lost exactly those two classes and the `/vesselantiafk` command. Everything else
in 1.8.0, including the Vault challenge guard and its log, is unchanged.

## Why it was retired

The player-side QA in `RELEASE_1.8.0.md` section 3 showed the feature only half worked. Its 5-minute pull,
the part a player notices, never fired: Vault Hunters re-targets the same player several times a second,
`LivingChangeTargetEvent` fires on each one, and `onTargetChanged` stamped engagement every time, so the
timer never reached `TIMEOUT_TICKS`. The owner chose to remove the whole feature rather than keep a partly
working anti-cheese in the mod.

## What this reopens, for the Wolds maintainer

Removing it reopens one exploit that the mod demonstrably blocked, so it is written down here rather than
dropped silently.

**The exploit.** `TheVesselEntity.hurt` ignores every damage source except a player attack and
`DamageSource.OUT_OF_WORLD`, so the void is the only passive way a Vessel can die. A Vessel that leaves its
arena (in QA it jumped its own 2-block fence while pathing toward the player, twice) falls below the world
and dies to `OUT_OF_WORLD` on its own. A player can therefore let a glitched Vessel kill itself while being
AFK, and the Greed fight resolves without being fought.

**What 1.8.0 did about it.** It recorded the Vessel's spawn position on `EntityJoinWorldEvent`, persisted that
anchor in entity NBT so it survived chunk reloads and restarts, and on `LivingAttackEvent` with
`OUT_OF_WORLD` it cancelled the damage and teleported the Vessel back to the anchor. QA confirmed both
rescues and the restart persistence.

**Also gone:** the 120-second lost-target pull, which teleported a player who had walked out of the Vessel's
target range (about 36 blocks) back onto it. That one worked as intended.

**If it is ever wanted back,** the Vessel is Vault Hunters' own boss
(`the_vault:the_vessel`, `iskallia.vault.entity.boss.TheVesselEntity`), so the durable place for a fix is a
mod that already mixes into it. Wold's Vaults does, through `MixinGreedVesselEntity`. The git history of this
repo holds the full implementation, and `RELEASE_1.8.0.md` section 3 holds the QA evidence and the engagement
-stamping bug to avoid repeating.

## Verification

- `./gradlew build` passes. The 1.8.1 jar differs from 1.8.0 by exactly the two removed classes; no other
  entry changed.
- Boot-tested on the local IRIS copy with the rest of the drop: see the drop's `INSTALL.md`.
