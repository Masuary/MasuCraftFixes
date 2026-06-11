# MasuCraftFixes - Plan

## Completed

### Vault Sync Telemetry Controls (2026-06-11, v1.7.1)
**Problem:** Vault sync telemetry proved the mitigation was working, but the `INFO` summaries and slow-packet diagnostics spammed the server console during active vaults.

**Solution:** Moved VaultSync diagnostics behind persisted runtime settings:
- Default telemetry is off.
- When enabled, diagnostics write to `logs/masucraftfixes-vaultsync.log` instead of Log4j console.
- Runtime settings persist in `config/masucraftfixes-vaultsync.properties`.
- Added `/vaultsync` admin commands:
  - `/vaultsync status`
  - `/vaultsync debug true|false` (alias for telemetry on/off)
  - `/vaultsync telemetry true|false`
  - `/vaultsync log-each-sync true|false`
  - `/vaultsync summary-interval <ticks>`
  - `/vaultsync slow-threshold <ms>`
  - `/vaultsync full-refresh-interval <ticks>`

Operational note: increasing `fullRefreshIntervalTicks` reduces periodic full baselines but makes client-side vault state rely on HUD diffs for longer between healing full syncs.

### Wolds Vault Sync Mitigation (2026-06-10, v1.7.0)
**Problem:** Vault Hunters `Listener.tickServer` sends a `SyncMode.FULL` `VaultMessage.Sync` every tick for every online vault listener. On modifier-heavy Wolds vaults this serializes the full client vault tree on the server thread and can stall TPS or trip the watchdog.

**Solution:** Added a narrow server-side mixin patch:
- Skip active sync when the listener's player is no longer in that virtual vault world.
- Keep vanilla FULL packets for first sync, join/rejoin baseline resets, finish, modifier-count changes, and periodic staggered refreshes.
- Replace normal per-tick FULL packets with root-level `SyncMode.DIFF` packets containing HUD-critical roots: `VERSION`, `ID`, `LEVEL`, `CLOCK`, `LISTENERS`, `OBJECTIVES`, `OVERLAY`, `COMPANION_EGG_HUNT`, `SOUND`, and `FINISHED` when present.
- Add `VaultMessage.Sync` telemetry for payload bytes, constructor time, offworld skips, state resets, and FULL/HUD_DIFF reason summaries.

Runtime system-property kill switches/defaults:
- `masucraftfixes.vaultSync.offworldGuard=true`
- `masucraftfixes.vaultSync.hudDiffEnabled=true`
- `masucraftfixes.vaultSync.fullRefreshIntervalTicks=20`
- `masucraftfixes.vaultSync.forceFullOnModifierCountChange=true`

If a client regression appears, set `-Dmasucraftfixes.vaultSync.hudDiffEnabled=false` to fall back to vanilla FULL sync behavior while keeping the offworld guard and telemetry.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/VaultSyncPolicy.java` (new)
- `src/main/java/com/masuary/masucraftfixes/VaultSyncTelemetry.java` (new)
- `src/main/java/com/masuary/masucraftfixes/mixin/VaultListenerSyncReplaceMixin.java` (new)
- `src/main/java/com/masuary/masucraftfixes/mixin/VaultMessageSyncTelemetryMixin.java` (new)
- `build.gradle` (version bumped to 1.7.0, VH compile target updated to 3.21.5.6573)
- `mixins.masucraftfixes.json` (registered vault sync mixins)

### Angel Expertise Flight Fix (2026-03-28)
**Problem:** AngelExpertise.onTick() strips FTB `/fly` flight every tick when player isn't near an Angel Block, applying Slow Falling instead.

**Solution:** Added `AngelExpertiseMixin` that cancels `onTick()` when `FTBEPlayerData.fly == true`, preventing AngelExpertise from interfering with FTB-granted flight.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/mixin/AngelExpertiseMixin.java` (new)
- `build.gradle` (added the_vault dependency)
- `mixins.masucraftfixes.json` (registered mixin)

### Crafting Tweaks Compress Dupe Fix (2026-04-30, v1.5.0)
**Problem:** Crafting Tweaks `COMPRESS_ONE` (Ctrl+K) hardcodes `craftsPossible = 1` in `CompressMessage.compressMouseSlot` without checking that the input stack has `recipeSize` items. Result: 1 iron ingot -> 1 iron block, server-wide dupe for any compressible item. Upstream issue [#202](https://github.com/TwelveIterationMods/CraftingTweaks/issues/202) labelled `lts` but never backported to the 1.18.2 line; 14.0.9 (Dec 2023) is the final 1.18.2 build and still ships the bug.

**Solution:** Added `CompressMessageMixin` with two injections inside `compressMouseSlot`:
- `@Redirect` on `mouseStack.shrink(itemsToRemove)` -- if `count < amount`, skip the shrink and set a thread-local flag.
- `@ModifyArg` on `addCraftedItemsToInventory(..., timesCrafted)` -- when flagged, force `timesCrafted` to `0` so the result loop produces nothing.

`COMPRESS_STACK` (Ctrl+Shift+K) and `COMPRESS_ALL` paths use correct count math and are not touched. Decompress paths have their own guard and are not touched.

**Files:**
- `deps/craftingtweaks-forge-1.18.2-14.0.9.jar` (new, copied from VH3R instance mods folder)
- `src/main/java/com/masuary/masucraftfixes/mixin/CompressMessageMixin.java` (new)
- `build.gradle` (added craftingtweaks dep, version bumped 1.4.0 -> 1.5.0)
- `mixins.masucraftfixes.json` (registered mixin)
- `META-INF/mods.toml` (added mandatory `craftingtweaks` dependency, `versionRange="[14.0.9,)"`)

**Coexistence note:** If a future VH update bundles a fixed Crafting Tweaks or adds its own Mixin on the same method, watch the server log on first boot. Our injections are strict (`defaultRequire = 1`); if VH's version restructures `compressMouseSlot` so the `INVOKE shrink` site disappears, Mixin will throw a critical injection failure at startup. If that happens: remove `"CompressMessageMixin"` from `mixins.masucraftfixes.json` and rebuild. If their fix coexists with our targets, the mixin runs redundantly with no behavioral change.
