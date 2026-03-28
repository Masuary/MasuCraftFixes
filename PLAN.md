# MasuCraftFixes - Plan

## Completed

### Angel Expertise Flight Fix (2026-03-28)
**Problem:** AngelExpertise.onTick() strips FTB `/fly` flight every tick when player isn't near an Angel Block, applying Slow Falling instead.

**Solution:** Added `AngelExpertiseMixin` that cancels `onTick()` when `FTBEPlayerData.fly == true`, preventing AngelExpertise from interfering with FTB-granted flight.

**Files:**
- `src/main/java/com/masuary/masucraftfixes/mixin/AngelExpertiseMixin.java` (new)
- `build.gradle` (added the_vault dependency)
- `mixins.masucraftfixes.json` (registered mixin)
