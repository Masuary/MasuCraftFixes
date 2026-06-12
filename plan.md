# VaultSync, Spark, and Wolds Performance Fix Plan

Source diagnosis:

- `yTZhEvScpw-report.md`
- `/home/masuary/Downloads/yTZhEvScpw.sparkprofile`
- `/home/masuary/Downloads/masucraftfixes-vaultsync.log`
- MasuCraftFixes source: `/mnt/data/MasuCraft Mods/MasuCraftFixes/src/main/java/com/masuary/masucraftfixes`
- Wolds source: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod`

This plan now tracks both implementation status and remaining validation work.

## Current Status

- Stage 0 MasuCraftFixes-only telemetry: implemented.
- Stage 0 build validation: passed with `./gradlew build`.
- Built artifact: `build/libs/masucraftfixes-wolds-1.7.1.jar`.
- Backend 1 boot validation: passed on 2026-06-12.
- Backend 1 test location: `/mnt/data/Test Server/`.
- Stage 1 Wolds filter necklace behavior fix: not implemented.
- Stage 2 hammer-adjacent behavior fixes: not implemented.
- Stage 3 virtual-world behavior fixes: not implemented.
- Wolds jar changes: not started.

Backend 1 boot validation result:

1. Installed `build/libs/masucraftfixes-wolds-1.7.1.jar` in `/mnt/data/Test Server/Backend 1/mods/`.
2. Backed up the previous `masucraftfixes-wolds-1.7.0.jar` under `/mnt/data/Test Server/Backend 1/disabled-masucraftfixes-stage0-test/`.
3. Server reached normal started state: `Done (12.078s)!`.
4. `/masucraftstage0 status` was registered and returned disabled-by-default Stage 0 counters.
5. `config/masucraftfixes-stage0.properties` was created.
6. Server shutdown saved all dimensions.
7. No MasuCraftFixes, Stage 0, or optional Wolds telemetry mixin crash appeared in `logs/latest.log`.

Next required validation:

1. Run with diagnostics enabled during one controlled mapped vault.
2. Confirm `logs/masucraftfixes-stage0.log` receives `stage0_summary` lines.
3. Confirm diagnostic overhead is not visible in Spark.
4. Confirm VaultSync packet mix remains stable.

## Summary Verdict

VaultSync is not the main remaining TPS problem in the captured profile.

The VaultSync mitigation is working:

- Parsed packet rows: `16660`
- `HUD_DIFF`: `15818`, or `94.95%`
- `FULL`: `842`, or `5.05%`
- `HUD_DIFF` median payload: `424 bytes`
- `HUD_DIFF` median build time: `0.043 ms`
- `FULL` median payload: `583904 bytes`
- `FULL` median build time: `7.440 ms`
- Only two `>20 ms` sync builds were found, both periodic full baselines in the 5-player vault.
- No VaultSync errors, warnings, disabled-state lines, LuckPerms anomalies, or exceptions were found in the VaultSync log.

The remaining low TPS is best explained by active mapped-vault gameplay load:

- Root Cause 1: Hammer mining fanout.
- Root Cause 2: Item pickup event pipeline.
- Root Cause 3: Vault virtual-world worker completion waits.

Important constraint: hammers are supported Vault Hunters gameplay. The plan does not disable or cap hammers. It reduces multiplied work around hammers.

## Evidence Summary

Profile window:

- Started: `2026-06-11 23:47:52` Europe/Amsterdam
- Ended: `2026-06-11 23:50:54` Europe/Amsterdam
- Duration: `181.7 s`
- Ticks: `1572`
- Average TPS: `8.65`
- Minecraft/Forge: `1.18.2` / Forge `40.3.11`
- Spark: `1.10.38`
- Sampler mode: `EXECUTION`
- Interval: `2000 us`

System health:

- Process CPU: about `36-38%`
- Heap used: about `20435 MB`
- G1 old collections: `0`
- This does not look like full-machine CPU saturation or old-gen GC collapse.

Active vault context:

- 10 active vault recipients.
- 5 active vault instances.
- The heavy vault was likely `1b516871-4823-4069-88bd-d04bbe0f3ecd`, with 5 active players.

Server-thread hotspots:

- Player tick path: `25.94%`
- `VirtualWorlds.tick -> ThreadPool.awaitCompletion -> IntLatch.waitUntil`: `24.16%`
- `ServerboundPlayerActionPacket -> handleBlockBreakAction`: `17.78%`
- `ForgeEventFactory.onPostServerTick`: `13.85%`
- `Player.touch -> ItemEntity.playerTouch -> ForgeEventFactory.onItemPickup`: `8.38% / 6.42%`
- `ServerChunkCache` main-thread task drain: `5.70%`

## Ownership Split

Use Wolds as the preferred fix location for Wolds-owned mechanics:

- Filter necklace pickup behavior.
- Wolds magnet behavior.
- Wolds map/layout safeguards.
- Client-side tooltip, screen, or config visibility for Wolds-owned features.

Use MasuCraftFixes for:

- VaultSync policy and telemetry.
- Cross-mod server diagnostics.
- Server-only guardrails that do not belong to Wolds.
- Compatibility mixins into Vault Hunters only where there is no cleaner Wolds-owned fix.

Do not move VaultSync into Wolds unless there is a specific client HUD behavior that needs Wolds-side support. Current VaultSync behavior is server-side mitigation and belongs in MasuCraftFixes.

## VaultSync Data Flow Baseline

Current call path:

```text
Listener.tickServer
  -> MasuCraftFixes VaultListenerSyncReplaceMixin
    -> VaultSyncPolicy.shouldSkipOffworld
    -> VaultSyncPolicy.shouldSendVanillaFull
      -> vanilla VaultMessage.Sync(FULL), or
      -> reduced VaultMessage.Sync(DIFF) with createHudDiffVault
```

Relevant MasuCraftFixes files:

- `src/main/java/com/masuary/masucraftfixes/mixin/VaultListenerSyncReplaceMixin.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncPolicy.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncTelemetry.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncConfig.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncCommand.java`

Persistence behavior:

- VaultSync state is in memory only.
- State is keyed by player UUID and vault UUID.
- State resets on listener join, listener leave, player logout, offworld skip, and expiry.
- It does not write Vault saved data.
- It does not write Wolds data.
- It does not write crystal NBT.
- It does not write player inventory.

Regression guard:

- Do not modify VaultSync while fixing Root Cause 1, Root Cause 2, or Root Cause 3.
- Keep VaultSync telemetry available to confirm that no change reintroduces full-sync spam.
- Expected post-fix VaultSync shape should remain mostly `HUD_DIFF`, with full syncs limited to first sync, periodic refresh, modifier count changes, and finish handling.

## Stage 0: Baseline Instrumentation

Status: implemented in MasuCraftFixes; build passed; Backend 1 boot test passed on 2026-06-12.

Goal: make the next code changes measurable and reversible.

Implementation target:

- Use MasuCraftFixes only for Stage 0 live-server telemetry.
- Do not require a Wolds jar update for Stage 0.
- Wolds remains the preferred later fix location for Wolds-owned behavior, but Stage 0 should observe it from MasuCraftFixes.
- Use Forge event observers and narrowly scoped, behavior-neutral timing mixins from MasuCraftFixes where source-level Wolds detail is needed.

Recommended diagnostics:

- Pickup events per player per vault.
- Wolds filter necklace handler invocation count.
- Wolds filter necklace handler elapsed time.
- Wolds `FilterNecklaceItem.stackMatchesFilter` invocation count and elapsed time.
- Wolds `FilterNecklaceItem.getInventory` invocation count and elapsed time.
- Wolds filter voided item count, inferred from pickup cancellation plus item discard where safely observable.
- Estimated cache opportunity, such as repeated necklace `Inventory` NBT hashes and repeated picked-up item types.
- Item entity count per vault world.
- Hostile mob count per vault world.
- Loaded chunk count per vault world.
- Per virtual-world tick duration where accessible.
- Hammer-related block-break burst size per player/tick.

Runtime requirements:

- Diagnostics must be gated by config or command.
- Counters should aggregate and periodically summarize rather than logging every event by default.
- Avoid per-pickup string formatting or file writes in hot paths.
- Stage 0 timing mixins must not alter return values, cancellation state, item stacks, NBT, entity removal, or event priority.
- Any optional Wolds-targeting mixin should fail closed or be individually disableable if the Wolds class or method signature changes.

Validation:

- Backend 1 boot test passed: the test server from `/mnt/data/Test Server/Backend 1` reached `Done (12.078s)!` with the new MasuCraftFixes jar.
- `/masucraftstage0 status` returned disabled-by-default counters and confirmed command registration.
- Shutdown saved all dimensions cleanly.
- Run with diagnostics enabled during one controlled mapped vault.
- Confirm diagnostic overhead is not visible in Spark.
- Confirm VaultSync packet mix remains stable.
- Confirm Stage 0 can be deployed by updating only the MasuCraftFixes jar.

Implemented files:

- `src/main/java/com/masuary/masucraftfixes/Stage0Telemetry.java`
- `src/main/java/com/masuary/masucraftfixes/Stage0TelemetryConfig.java`
- `src/main/java/com/masuary/masucraftfixes/Stage0TelemetryCommand.java`
- `src/main/java/com/masuary/masucraftfixes/Stage0TelemetryEvents.java`
- `src/main/java/com/masuary/masucraftfixes/mixin/WoldsFilterNecklaceItemTelemetryMixin.java`
- `src/main/java/com/masuary/masucraftfixes/mixin/WoldsPlayerEventsTelemetryMixin.java`
- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixes.java`
- `src/main/java/com/masuary/masucraftfixes/MasuCraftFixesMixinPlugin.java`
- `src/main/resources/mixins.masucraftfixes.json`

Implemented command:

```text
/masucraftstage0 status
/masucraftstage0 telemetry true|false
/masucraftstage0 debug true|false
/masucraftstage0 log-slow-events true|false
/masucraftstage0 summary-interval <ticks>
/masucraftstage0 world-sample-interval <ticks>
/masucraftstage0 slow-threshold <ms>
/masucraftstage0 top-entry-limit <limit>
/masucraftstage0 reset
```

Implemented output:

- Config: `config/masucraftfixes-stage0.properties`
- Log: `logs/masucraftfixes-stage0.log`
- Periodic summaries for pickups, block breaks, Wolds filter timing, and virtual-world samples.

Implemented startup kill switches:

- `-Dmasucraftfixes.stage0.woldsMixins=false`
- `-Dmasucraftfixes.stage0.woldsPlayerEventsMixin=false`
- `-Dmasucraftfixes.stage0.woldsFilterNecklaceItemMixin=false`

Completed validation:

- `./gradlew build` passed.
- The built jar contains the Stage 0 telemetry classes and Wolds optional telemetry mixins.

Regression risks:

- Diagnostic logging can become the new lag source if it logs per event.
- World/entity telemetry can touch hot paths. Keep reads cheap and sampled.
- Timing mixins into Wolds methods can break on Wolds updates. Keep them isolated, optional, and telemetry-only.

## Stage 1: Fix Root Cause 2, Wolds Filter Necklace

Status: pending. Stage 0 telemetry for Wolds filter necklace timing is implemented in MasuCraftFixes, but the Wolds behavior/cache fix itself is not implemented.

This is the lowest-risk first fix because the measured hotspot is Wolds-owned and local to one feature.

Current Wolds path:

```text
Player.touch
  -> ItemEntity.playerTouch
    -> ForgeEventFactory.onItemPickup
      -> Wolds PlayerEvents.onFilterNecklaceUse
        -> FilterNecklaceItem.getNecklace through Curios
        -> ServerVaults.get(world).isPresent
        -> FilterNecklaceItem.stackMatchesFilter
          -> getInventory
            -> new ItemStackHandler
            -> deserialize Inventory NBT
          -> loop slots
          -> optional VFTests.checkFilter
```

Relevant Wolds files:

- `src/main/java/xyz/iwolfking/woldsvaults/events/PlayerEvents.java`
- `src/main/java/xyz/iwolfking/woldsvaults/items/filter_necklace/FilterNecklaceItem.java`
- `src/main/java/xyz/iwolfking/woldsvaults/items/filter_necklace/menus/FilterNecklaceMenu.java`
- `src/main/java/xyz/iwolfking/woldsvaults/client/screens/FilterNecklaceContainerScreen.java`

Proposed change:

1. In `onFilterNecklaceUse`, check cheap conditions first:
   - player is `ServerPlayer`
   - item stack is not empty
   - player world is a vault world
2. Only after the vault-world check, call `FilterNecklaceItem.getNecklace(player)`.
3. Add a parsed filter cache in Wolds.
4. Key the cache by serialized necklace `Inventory` NBT hash or equivalent immutable representation.
5. Precompute exact item matches into a fast set.
6. Keep Create/VaultFilters filter stacks as filter stacks and only call `VFTests.checkFilter` after exact matching fails.
7. Preserve the same event priority, cancellation behavior, item discard behavior, and pickup sound.
8. Clear any per-player cache entries on logout, but rely on NBT hash as the real invalidation mechanism.

Expected effect:

- Reduces Curios lookup outside vaults.
- Avoids rebuilding `ItemStackHandler` from NBT on every pickup.
- Avoids repeated slot scanning for exact item matches.
- Reduces calls to `VFTests.checkFilter`.

Persistence behavior:

- The necklace `Inventory` NBT remains the source of truth.
- Cache is in memory only.
- Editing the necklace inventory through the menu updates NBT as before.
- Cache invalidates naturally when the serialized inventory NBT changes.
- No new saved data is required.

Client-side considerations:

- No client change is required for the performance fix.
- If a config toggle or debug state is added, expose it in Wolds-owned tooltip/config UI only if useful.
- Do not change the menu wire format unless necessary.

Validation:

- Necklace voids the same exact items before and after.
- Necklace voids the same filter-matched items before and after.
- Editing the necklace inventory immediately changes server behavior.
- Empty necklace does nothing.
- No necklace does nothing.
- Necklace outside vault does nothing.
- Necklace inside vault still cancels pickup, discards the item entity, and plays the pickup sound when matched.

Runtime checks:

- Cache hit rate should be high during repeated pickup.
- `VFTests.checkFilter` calls should drop sharply.
- Wolds filter necklace sample share should fall in Spark.
- `ForgeEventFactory.onItemPickup` sample share should fall if necklace was a meaningful part of the workload.

Regression risks:

- Stale cache could void wrong items. Mitigation: key by `Inventory` NBT hash, not only by player or stack object.
- Cache could leak if keyed by player forever. Mitigation: clear on logout and size-limit if needed.
- Event interaction could change if priority changes. Mitigation: do not change priority.
- Client menu could produce unexpected NBT shape. Mitigation: keep existing `getInventory` and `saveToStack` behavior.

## Stage 2: Fix Root Cause 1, Hammer Mining Fanout

Status: pending. Stage 0 block-break and per-player/tick burst telemetry is implemented; hammer-adjacent behavior fixes are not implemented.

Do not nerf or disable hammers.

Current path:

```text
ServerboundPlayerActionPacket
  -> ServerGamePacketListenerImpl.handlePlayerAction
    -> ServerPlayerGameMode.handleBlockBreakAction
      -> Vault Hunters hammer mixin
        -> ToolItem.getHammerPositions
          -> square candidate iteration
          -> blockstate reads
          -> ForgeHooks.onLeftClickBlock per candidate
        -> repeated block break handling
          -> block break events
          -> drops
          -> item entities
          -> pickup listeners
```

Evidence:

- Packet/block-break path: `17.78%`
- VH hammer mixin: `16.25%`
- `ArrayList.removeIf` over hammer tiles: `10.94%`
- `ToolItem.getHammerPositions`: `5.15%`
- `ForgeHooks.onLeftClickBlock` inside hammer-position iteration: `5.13%`

Proposed low-risk sequence:

1. Apply Stage 1 first.
2. Re-profile hammer mining with the same player behavior.
3. Add telemetry for:
   - hammer burst size
   - block-break events per player/tick
   - item entities spawned per burst
   - pickup events following bursts
4. Do not bypass `ForgeHooks.onLeftClickBlock`.
5. Do not cache hammer positions across ticks.
6. Do not skip block-break hooks.
7. Consider only narrowly scoped micro-optimizations after telemetry.

Potential micro-optimization:

- If a safe mixin into VH `ToolItem.getHammerPositions` is justified, reorder candidate Y-bound checks before blockstate reads for out-of-world positions.
- This is low semantic risk but likely low impact unless mining near world floor or ceiling.

Potential later optimization:

- Item entity coalescing for vault mining drops.
- This should not be first-pass because it can alter gameplay if implemented too broadly.

If item entity coalescing is later approved:

- Put it behind a config kill switch.
- Only handle exact `ItemEntity.class`.
- Do not handle custom subclasses.
- Only merge identical item and NBT.
- Preserve owner, thrower, pickup delay, age, and position locality.
- Exclude special Vault Hunters and Wolds entities.
- Exclude items with behavior-sensitive tags unless proven safe.

Persistence behavior:

- Hammer block changes remain vanilla/VH behavior.
- No new saved data should be introduced.
- Item coalescing, if added later, changes runtime entity shape only and must not alter final item counts.

Interactions with Wolds/Vaults:

- Wolds spawner break cancellation must continue to work.
- Ars/reactive or other left-click cancellation must continue to work.
- VH vault chest protection must continue to work.
- Mining quests, bounties, bingo, scavenger, and vault objectives must continue to receive expected events.
- Wolds client hammer highlighting should remain consistent with server mining behavior.

Validation:

- Hammer breaks the same valid blocks.
- Hammer refuses the same invalid/protected blocks.
- Vault chests are not broken unless existing VH rules allow it.
- Spawner-breaking restrictions remain active.
- Drops are neither duplicated nor lost.
- Mining-related quests and objectives still count.
- Spark shows lower pickup/listener/item-entity cost before any hammer semantic patch is attempted.

Regression risks:

- Bypassing left-click hooks can break protection and mod compatibility.
- Coalescing drops can affect ownership, pickup delay, quests, or pickup order.
- Changing hammer geometry can desync client highlight from server behavior.

## Stage 3: Fix Root Cause 3, Virtual World Worker Completion

Status: pending. Stage 0 read-only virtual-world sampling is implemented; no virtual-world behavior or threading changes have been made.

Do not patch `IntLatch.waitUntil`, `ThreadPool.awaitCompletion`, or the virtual-world wait directly.

Current path:

```text
Server tick START
  -> VirtualWorlds schedules concurrent vault-world tick jobs
Vault worker threads
  -> VirtualWorlds.tickWorld
  -> ServerLevel.tick for virtual world
  -> entity ticking, chunk ticking, generation work
Server tick END
  -> VirtualWorlds.tick
  -> ThreadPool.awaitCompletion
  -> IntLatch.waitUntil
  -> Object.wait until workers finish
```

Interpretation:

- The wait stack is real.
- The wait method is not the expensive gameplay logic.
- The main server thread is blocked until vault-world worker jobs complete.
- Fix worker duration, not the latch.

Worker-side evidence:

- `VirtualWorlds.tickWorld`: `4.43%`
- `ServerLevel.tick` inside virtual world: `4.43%`
- `EntityTickList.forEach`: `3.08%`
- `Zombie.tick`: `1.54%`
- `ItemEntity.tick`: `0.54%`
- `ServerChunkCache.tick`: `1.07%`

Mapped vault generation evidence:

- `ChunkMap...VaultGeneration -> DummyChunkGenerator -> GridGenerator.generate -> SectionedTemplate.place`
- `Vault-Gen -> SectionedTemplate.place -> BatchBlockPlacer.placeTiles`
- This likely explains max-MSPT spikes, but not all sustained low TPS.

Wolds map/layout interaction:

- `VaultMapItem.applyCrystalRecipe` modifies the crystal before runtime.
- Map tier adds size using `(MAP_TIER + 1) * 10`.
- Layout manipulators can replace or extend crystal layout.
- Wolds map items do not show as runtime hot stacks directly, but they can create larger/heavier vaults that later cost more in VH virtual worlds.

Relevant Wolds files:

- `src/main/java/xyz/iwolfking/woldsvaults/items/gear/VaultMapItem.java`
- `src/main/java/xyz/iwolfking/woldsvaults/recipes/crystal/LayoutModificationRecipe.java`

Proposed low-risk sequence:

1. Add read-only per-vault-world telemetry.
2. Correlate long waits with item entities, mobs, chunks, generation, and active players.
3. Let Stage 1 and Stage 2 reduce item pressure before changing virtual-world behavior.
4. If generation is the issue, prefer Wolds-side map/layout safeguards or operational limits.
5. If mobs are the issue, do not immediately throttle AI because that changes vault difficulty.

Potential Wolds-side safeguards:

- Configurable warning for extreme mapped crystal size.
- Client tooltip showing expected added size and possible server impact.
- Server-side config cap for map-added size, defaulting to current behavior unless explicitly enabled.
- Server-side warning when combining map/layout settings likely to create expensive vaults.

Persistence behavior:

- Wolds map/layout changes persist in crystal NBT before vault start.
- Runtime telemetry should be in memory only.
- Do not modify virtual-world saved entries.
- Do not modify vault deletion lifecycle.
- Do not alter threading or worker completion semantics.

Validation:

- Compare standing still, hammer mining, looting, and new-room traversal phases separately.
- Track per-vault-world item entities, mobs, chunks, and tick duration.
- Verify vault completion and objective behavior.
- Verify mapped vault generation still creates expected rooms/layout.
- Confirm no race conditions or crashes in virtual worlds.

Regression risks:

- Changing virtual-world threading can corrupt tick order. Mitigation: do not change it.
- Mob throttling can alter difficulty and objective outcomes. Mitigation: telemetry first.
- Map size caps can affect progression/economy. Mitigation: default off or warning-only first.
- Entity cleanup can break objectives. Mitigation: avoid cleanup until exact cause is proven.

## Cross-Stage Validation Plan

Backend 1 boot validation:

1. Completed: deploy `build/libs/masucraftfixes-wolds-1.7.1.jar` to Backend server 1 in `/mnt/data/Test Server/`.
2. Completed: start Backend server 1.
3. Completed: verify the server reaches normal started state.
4. Completed: verify there are no MasuCraftFixes, Stage 0, or Wolds optional telemetry mixin crashes.
5. Completed: run `/masucraftstage0 status`.
6. Run `/masucraftstage0 telemetry true`.
7. Confirm `logs/masucraftfixes-stage0.log` receives `stage0_summary` lines after the summary interval.
8. Confirm `/vaultsync status` still works and VaultSync telemetry behavior is unchanged.

Use controlled profiles during the same kind of mapped vault:

1. Stand still in the mapped vault for 60-120 seconds.
2. Mine with the normal hammer setup for 60-120 seconds.
3. Mine with the same hammer setup but remove or bypass filter necklace, magnets, backpacks, and pendant voiding if possible.
4. Loot with magnets/filter necklace/backpacks enabled but avoid active mining for 60-120 seconds.
5. Traverse new mapped rooms to isolate generation spikes.

Suggested Spark captures:

```text
/spark profiler --timeout 180 --thread * --only-ticks-over 80
/spark tickmonitor
/spark healthreport
```

Expected interpretation:

- If standing still is still bad, virtual-world entity/chunk pressure is the main cause.
- If hammer mining causes the drop, hammer-amplified block/drop/pickup work is the main cause.
- If looting causes the drop, pickup listeners and item entity volume are the main cause.
- If only new-room traversal spikes, mapped vault generation/chunk placement is the spike cause.

## Acceptance Criteria

VaultSync:

- Packet mix remains mostly `HUD_DIFF`.
- No full-sync spam returns.
- No VaultSync errors or disabled-state lines appear.
- No client HUD regressions are reported.

Root Cause 2:

- Wolds filter necklace behavior is unchanged from the player's perspective.
- Cache hit rate is high in repeated pickup scenarios.
- `VFTests.checkFilter` call count is materially lower.
- Wolds filter necklace sample share drops in Spark.

Root Cause 1:

- Hammers remain fully supported.
- Same blocks break or are blocked as before.
- Drops are not duplicated or lost.
- Mining tasks/objectives still count.
- Pickup and item entity pressure decreases.

Root Cause 3:

- `IntLatch.waitUntil` only decreases if worker duration decreases.
- Per-vault-world telemetry explains the remaining waits.
- No virtual-world race, crash, or lifecycle regression appears.
- Mapped vault generation remains functionally correct.

## Recommended Implementation Order

1. Completed: add low-overhead MasuCraftFixes-only telemetry and validation counters.
2. Completed: build the MasuCraftFixes jar with Stage 0 telemetry.
3. Completed: deploy Stage 0 by updating only the MasuCraftFixes jar on Backend server 1 under `/mnt/data/Test Server/`.
4. Completed: boot Backend server 1 and verify there are no MasuCraftFixes, Stage 0, or Wolds optional telemetry mixin regressions.
5. Pending: re-profile the same mapped vault scenarios and confirm whether Wolds filter necklace, hammer fanout, or virtual-world worker duration is the highest-value next fix.
6. Pending: when a Wolds jar update is available, implement Wolds filter necklace cache and cheap short-circuits.
7. Pending: re-profile mapped vault hammer/loot behavior.
8. Pending: decide whether hammer-adjacent item entity reduction is still needed.
9. Pending: add or refine virtual-world read-only telemetry if Stage 0 did not explain worker waits.
10. Pending: decide whether Wolds map/layout warnings or caps are needed.
11. Pending: only after evidence, consider guarded item entity coalescing or narrowly scoped VH compatibility micro-optimizations.

## Explicit Non-Goals

- Do not disable hammers.
- Do not cap hammer size as a first-line fix.
- Do not bypass `ForgeHooks.onLeftClickBlock`.
- Do not patch `IntLatch.waitUntil` or skip virtual-world worker completion.
- Do not change VaultSync while fixing these root causes.
- Do not add armor-stand or visual entity workarounds for unrelated nametag features.
