# Deep Audit Snapshot - VesselAntiAfk

**Scope:** VesselAntiAfk against VH 3.21.2 source
**Date:** 2026-05-29
**Git SHA:** 6e5656a
**Verdict:** 🟡 YELLOW

> Snapshot from one audit pass. Findings reflect the code as of the SHA above; line numbers and severities will drift as fixes land. Do not treat this as a living spec - re-run `/deep-audit` for a current view.

## HIGH

- [x] **[src/main/java/com/masuary/masucraftfixes/VesselAntiAfk.java:46-55] Soul Tether passive damage does not stamp engagement.**
  why: `VesselSoulTetherHandler.tickPassive()` line 137 uses `DamageSource.f_19319_` (`MAGIC`, no entity). Our handler only stamps when victim is the Vessel or `source.getEntity()` is the Vessel - neither matches. Tether runs up to 600 ticks (30s) with target progressively slowed to level IV. A stationary tank >12 blocks away takes tether damage tick-by-tick with no stamp, triggering a false-positive teleport mid-tether. Fix: stamp when `victim == vessel.getTarget()` for any tracked Vessel, or check `target.hurtTime > 0` in the tick handler.
  Fixed by adding `target.hurtTime > 0` to the engagement signals in `onLivingTick`. Catches Soul Tether, phantom damage, and any other no-entity damage source universally.

## MEDIUM

- [x] [VesselAntiAfk.java:104-110] `onLivingDeath` is dead code - `TheVesselEntity.m_6667_/m_6074_` reset health to 1 without calling super, so `LivingDeathEvent` never fires. Cleanup is carried entirely by `EntityLeaveWorldEvent`. Delete the handler.
- [ ] [VesselAntiAfk.java:91] Distance gate uses live Vessel position. During long-cast stationary spells (Black Hole, Javelin Rain, Lightning Storm) where Vessel doesn't move and player is >12 blocks, the gate doesn't help - we rely on damage stamps only.
  Mitigated by HIGH fix (target.hurtTime stamp catches damage from these spells too). Leaving open as a defense-in-depth note.
- [ ] [VesselAntiAfk.java:155-163] `forceEngagement` teleports player to raw `vessel.getY()`. If Vessel is mid-air during a spell, player can land inside geometry. Use a small Y offset or sample a nearby safe block.
  Reassessed: Vessel position is by definition a valid 2-block-tall entity location, so player fits there. Mid-air spell case lands player in open air, not geometry. Not actually a bug. Closing without code change.
- [x] Damage-source attribution for `VesselBlackHoleHandler`, `VesselJavelinRainHandler`, and `VesselGhostCopyHandler` (phantom damage) is unverified. If any uses no-entity damage like Soul Tether, the HIGH finding applies there too.
  Verified: GravitySlam:188 and Dash:115 use `DamageSource.m_19370_(vessel)` (Vessel as source). BlackHole/JavelinRain/StaticBall/BlitzCombo damage via projectiles (separate attribution path). GhostCopy phantoms have Vessel as ownerUUID but damage source is the phantom itself. HIGH fix (target.hurtTime) covers all these cases universally.

## LOW

- [x] [VesselAntiAfk.java:25-27] `ConcurrentHashMap` is over-engineered (all handlers run on server main thread). `HashMap` is fine.
- [x] [VesselAntiAfk.java:122] `forgetVessel`'s only caller besides leave-world is `onLivingDeath` (dead - see MEDIUM).
  Inlined the three `.remove()` calls into `onEntityLeaveWorld` after deleting `onLivingDeath`; `forgetVessel` no longer exists.
- [ ] [VesselAntiAfk.java:128-138] Class-hierarchy-name walk runs on every event. Cache via `WeakHashMap` if it ever shows up in a profile.
  Skipped - premature optimization, not a bug.
- [ ] [VesselAntiAfk.java:140-153] `stampEngagement` re-resolves target every call. Split into `stampWithoutTargetPos` / `stampWithTargetPos` to skip the `instanceof Mob` cast on damage-event path.
  Skipped - cosmetic refactor.
- [ ] [VesselAntiAfk.java:20] Class-name string isn't refactor-safe. Cache `Optional<Class<?>>` on first event and use `isInstance`.
  Skipped - hypothetical concern, no current breakage.

## Recommended next actions

1. Fix the Soul Tether damage gap (either `target.hurtTime > 0` in tick, or a "victim is any tracked Vessel's target" branch in `onLivingDamage`).
2. Audit Black Hole / Javelin Rain / Ghost Copy damage source attribution.
3. Delete `onLivingDeath` and the matching `forgetVessel` call.
4. In-game stress test: trigger Soul Tether and Lightning Storm at >12 blocks with a stationary tank.
5. Consider bumping `MIN_TELEPORT_DISTANCE_SQ` to 400 (20 blocks) if false positives persist after #1.
