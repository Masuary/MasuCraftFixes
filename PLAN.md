# MasuCraftFixes - Plan

## Completed

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
