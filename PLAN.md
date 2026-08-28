# MasuCraftFixes - Plan

## Completed

### Orechid Ignem Nether-Biome Compatibility (2026-08-28, v1.6.1)
**Problem:** Botania 1.18.2-435 permits the Orechid Ignem to operate only when
the dimension type has a ceiling. MasuCraft's Nether is disabled, while
Overworld islands can use biomes in `#minecraft:is_nether`.

**Solution:** Added a server-side `OrechidIgnemMixin` that permits operation
when the biome at the flower's exact block position has the vanilla Nether
biome tag. The mixin leaves Botania's original ceiling-dimension rule intact,
does not run its added behavior on the logical client, and is skipped when
Botania is absent.

**Validation:**
- Clean Java 17 build passed against Botania `1.18.2-435`.
- The compile classpath resolved the exact CurseMaven artifact `3936568`.
- The reobfuscated JAR contains the mixin, config registration, server-side
  guard, biome lookup, and `BiomeTags.IS_NETHER` check.
- Dedicated-server gameplay validation remains required before deployment.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/mixin/OrechidIgnemMixin.java` (new)
- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixesMixinPlugin.java`
- `src/main/resources/mixins.masucraftfixes.json`
- `src/main/resources/META-INF/mods.toml`
- `build.gradle` (Botania compile target added, version bumped `1.6.0` -> `1.6.1`)

### Vault Build 6574 v1_66 Data Compatibility (2026-07-26, v1.6.0)
**Problem:** Vault Hunters build 6872 reuses the v1_66 schema version after changing
several versioned field registries. Worlds upgraded directly from build 6574 cannot
load `the_vault_Vaults.dat` or historical Vault snapshots because their v1_66 bit
streams are decoded with the new registry shape.

**Solution:** Added an exact-build migration profile for The Vault
`1.18.2-20.0.3-remastered.6872`. Before normal world loading, it temporarily
restores the build 6574 v1_66 definitions, atomically rewrites active Vault
records and historical snapshots as v1_67, verifies each replacement, and then
restores build 6872's native schema in the same process. Every changed original
is byte-verified under `world/data/masucraftfixes-v166-backup`, and a completion
marker is written to `world/data/masucraftfixes-v166-to-v167.properties`.

The profile is enabled by default for MasuCraft's controlled build 6574 to build
6872 rollout and ignored on every other Vault build. Once conversion succeeds,
the world contains standard v1_67 files and no longer depends on the temporary
reader. Disable it for a server from another source build with
`vaultDataCompatibility.enableLegacy6574V166Compatibility=false` in
`config/masucraftfixes-common.toml`.

**Validation:**
- Clean compile and build passed.
- A copy of the restored production world migrated all 18 active Vault records
  and all 8,220 historical snapshot files.
- The complete 244 MiB backup matched the pre-migration originals, including all
  8,220 snapshot files.
- Vault's native snapshot audit loaded all 8,220 references with zero corrupted
  snapshots after the temporary schema reader was removed.
- A cold restart with the compatibility setting disabled loaded the converted
  world successfully without the original saved-data error.
- The original production world was not modified during validation.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixesConfig.java` (new)
- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixesMixinPlugin.java` (new)
- `src/main/java/com/masuary/masucraftfixes/VaultV166Compatibility.java` (new)
- `src/main/java/com/masuary/masucraftfixes/VaultV166DiskMigration.java` (new)
- `src/main/java/com/masuary/masucraftfixes/mixin/VaultSnapshotV166CompatibilityMixin.java` (new)
- `build.gradle` (Vault dependency updated, version bumped 1.5.1 -> 1.6.0)
- `mixins.masucraftfixes.json` (registered exact-build mixin plugin and compatibility mixins)
- `META-INF/mods.toml` (updated description)

### Vault 20.0.3 Crafting Tweaks Compatibility (2026-07-26, v1.5.1)
**Problem:** Vault Hunters build 6872 added `MixinCraftingTweaksCompress`, which implements the same Crafting Tweaks dupe fix as `CompressMessageMixin`. Both mixins redirect the same `ItemStack.shrink(int)` call. Vault's strict injection therefore fails during startup after the MasuCraftFixes redirect applies first.

**Solution:** Removed `CompressMessageMixin` and the now-unused Crafting Tweaks build and runtime dependencies. Vault Hunters owns the dupe fix in 20.0.3 and later.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/mixin/CompressMessageMixin.java` (removed)
- `deps/craftingtweaks-forge-1.18.2-14.0.9.jar` (removed)
- `build.gradle` (removed dependency, version bumped 1.5.0 -> 1.5.1)
- `mixins.masucraftfixes.json` (removed mixin registration)
- `META-INF/mods.toml` (removed mandatory `craftingtweaks` dependency)

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

**Retired in v1.5.1:** Vault Hunters 20.0.3 build 6872 added its own strict redirect for the same fix. The MasuCraftFixes mixin and Crafting Tweaks dependency were removed to avoid a startup injection conflict.
