# MasuCraftFixes - Plan

## Completed

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
