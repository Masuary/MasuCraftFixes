# VaultSync, Spark, and Wolds Performance Fix Plan

Source diagnosis:

- `/home/masuary/Downloads/Logs/Y280i8UbT7-report.md`
- `/home/masuary/Downloads/Logs/Y280i8UbT7.sparkprofile`
- `/home/masuary/Downloads/Logs/2026-06-13-1.log`
- `yTZhEvScpw-report.md`
- `/home/masuary/Downloads/yTZhEvScpw.sparkprofile`
- `/home/masuary/Downloads/masucraftfixes-vaultsync.log`
- `/home/masuary/Downloads/Logs/masucraftfixes-stage0.log`
- `/home/masuary/Downloads/Logs/masucraftfixes-vaultsync.log`
- MasuCraftFixes source: `/mnt/data/MasuCraft Mods/MasuCraftFixes/src/main/java/com/masuary/masucraftfixes`
- Wolds source: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod`

This plan now tracks both implementation status and remaining validation work.

## Current Status

- Stage 0 MasuCraftFixes-only telemetry: implemented.
- Stage 0 build validation: passed with `./gradlew build`.
- Stage 0 live log and Spark validation: completed from `/home/masuary/Downloads/Logs/Y280i8UbT7.sparkprofile` and matching logs on 2026-06-13.
- Latest profile report: `/home/masuary/Downloads/Logs/Y280i8UbT7-report.md`.
- Built artifact from Stage 0: `build/libs/masucraftfixes-wolds-1.7.1.jar`.
- Backend 1 boot validation: passed on 2026-06-12.
- Backend 1 test location: `/mnt/data/Test Server/`.
- Stage 1 Wolds filter necklace behavior fix: not implemented; now classified as low-risk cleanup rather than the primary next TPS fix for the latest workload.
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

Latest Stage 0 live validation result:

1. `logs/masucraftfixes-stage0.log` captured 77 summaries inside the Spark window from `2026-06-13T11:29:57Z` through `2026-06-13T12:11:37Z`.
2. Final Stage 0 counters, reset shortly before the profile: `304761` vault pickup events, `300782` canceled pickups, `122706` removed item entities, `201164` vault block-break events, `10147` canceled block-break events, and `577` max block-break events for one player in one tick.
3. Wolds filter matching was active: `180793` handler calls, `91022` `stackMatchesFilter` calls, and `91022` `getInventory` calls. Average timings were low: handler `0.022 ms`, `stackMatchesFilter` `0.033 ms`, and `getInventory` `0.002 ms`.
4. Virtual-world sampling captured `462` samples, with max per-world tick time `230.007 ms`, max item entities `262`, max hostile mobs `851`, and max loaded chunks `56155`.
5. `logs/masucraftfixes-vaultsync.log` stayed stable for the same Spark window: delta `FULL=17034`, `HUD_DIFF=323567`, about `5.001%` full and `94.999%` HUD diff.
6. Server log correlation found `13767` `WorldGenRegion` block-entity-before-created warnings, `2292` duplicate entity UUID warnings, `116` keep-up or Waterframes overload warnings, and `61` Vault `Placing ERROR Block` lines during the Spark window.
7. The exact-thread Spark analysis confirms the main issue is not telemetry overhead: virtual-world worker waits, player tick, Curios/NBT equality, mapped generation/entity pressure, and hammer/block-action bursts are the actionable areas.

Next required validation:

1. Investigate the duplicate entity UUID and `WorldGenRegion` generation warnings, especially vaults `4e51ad50-f0d4-4c15-90f1-e12475bf7ca0` and `46f599ca-0c93-4759-83b6-aac8f41aa580`.
2. Refine virtual-world telemetry by vault id, entity type, chunk count, and warning correlation if the next fix needs more attribution.
3. Investigate the Curios/player equipment NBT equality cost under `ServerPlayer.tick`.
4. Keep VaultSync telemetry enabled for confirmation, but do not change VaultSync policy unless a new profile implicates it.

## Summary Verdict

VaultSync is not the main remaining TPS problem in the latest captured profile.

The latest capture, `Y280i8UbT7`, is degraded but not as severe as the older 8-10 TPS profile:

- Average TPS: `18.25`.
- Worst TPS window: `9.80 TPS`, `85.98 ms` median MSPT, `598.72 ms` max MSPT.
- Worst spike: `1842.49 ms` max MSPT.
- Process CPU stayed around `28-34%`, and G1 old collections remained `0`, so this is not full-machine saturation or old-gen GC collapse.

The remaining low TPS is best explained by mapped-vault load:

- Root Cause 1: Vault virtual-world worker completion waits and worker-side vault entity/chunk ticking.
- Root Cause 2: Player tick cost, including Curios/player equipment NBT equality.
- Root Cause 3: Mapped-vault generation/spawn anomalies, shown by duplicate entity UUID and `WorldGenRegion` warning volume.
- Root Cause 4: Hammer mining and pickup/event fanout remain active load multipliers, but were not the top sampled offender in this latest profile.

The Wolds filter necklace path was active in the latest run and has a likely cache opportunity, but Spark ranks it low: `Wolds PlayerEvents.onFilterNecklaceUse` was `2.5 ms`, `0.2%` of sampled server-thread time. Stage 1 remains reasonable cleanup, not the primary next TPS fix.

Important constraint: hammers are supported Vault Hunters gameplay. The plan does not disable or cap hammers. It reduces multiplied work around hammers.

## Latest Evidence Summary: Y280i8UbT7

Profile window:

- Started: `2026-06-13 13:29:54` Europe/Amsterdam
- Ended: `2026-06-13 14:11:56` Europe/Amsterdam
- Duration: `2522.2 s`
- Ticks: `46042`
- Average TPS: `18.25`
- Minecraft/Forge: `1.18.2` / Forge `40.3.11`
- Spark: `1.10.38`
- Sampler mode: `EXECUTION`
- Interval: `4000 us`

System health:

- Process CPU: about `28-34%`
- Heap used at capture end: about `15496 MB`
- G1 old collections: `0`
- Estimated total GC pause: about `23.7 s` over `2522.2 s`, about `0.94%` of wall time.
- This does not look like full-machine CPU saturation or old-gen GC collapse.

Worst Spark windows:

- Window `29689200`: `9.80 TPS`, `85.98 ms` median MSPT, `598.72 ms` max MSPT, `817` entities, `54926` chunks.
- Window `29689199`: `11.02 TPS`, `74.34 ms` median MSPT, `203.89 ms` max MSPT, `1004` entities, `58102` chunks.
- Window `29689188`: `18.17 TPS` but `1842.49 ms` max MSPT, `787` entities, `55864` chunks.
- Window `29689196`: max chunk count at `58458` chunks.

Exact `Server thread` findings:

- Main tick root: `MinecraftServer.m_5705_`, `1289.7 ms`, `83.3%`.
- Vault virtual-world wait: `VirtualWorlds.tick -> ThreadPool.awaitCompletion -> IntLatch.waitUntil -> Object.wait`, `436.2 ms`, `28.2%`.
- Player tick under connection tick: `ServerConnectionListener -> ServerGamePacketListenerImpl.m_9933_ -> ServerPlayer.tick`, `408.8 ms`, `26.4%`.
- Curios tick: `71.6 ms`, `4.6%`.
- Player equipment/NBT equality: `ItemStack.m_41728_ -> ItemStack.m_41744_ -> CompoundTag.equals`, `48.0 ms`, `3.1%`.
- Vault post-server-tick sync construction: vanilla/full `VaultMessage.Sync.<init>` `27.9 ms`, `1.8%`; MasuCraftFixes diff path `8.6 ms`, `0.6%`.
- Vault card/task post-tick work: `ActiveCardTaskHelper.onServerTick -> CardDeck.readNbt` `26.1 ms`, `1.7%`; `DeckRecipeTaskData.onServerTick` `22.5 ms`, `1.5%`.
- Hammer/block action path: `ServerboundPlayerActionPacket -> ServerPlayerGameMode.handleBlockBreakAction`, `63.9 ms`, `4.1%`.

Exact Vault virtual-world worker findings:

- Worker pool: `pool-24-thread (x10)`.
- Most worker samples are idle waiting for work, but active work is real vault ticking.
- `VirtualWorlds.tickWorld -> ServerLevel.tick`: `888.6 ms`, `5.8%` of the worker group.
- Entity ticking: `EntityTickList.m_156910_`, `614.8 ms`, `4.0%`; entity tick path `569.9 ms`, `3.7%`.
- `Mob.tick`: `226.9 ms`, `1.5%`.
- `Zombie.tick`: `103.3 ms`, `0.7%`.
- Chunk ticking/loading paths: `ServerChunkCache.m_201698_` `182.4 ms`, `1.2%`; `ServerChunkCache.m_8490_` `156.1 ms`, `1.0%`.

Stage 0 and log correlation:

- Stage 0 summaries inside Spark window: `77`.
- Vault pickups: `304761`; canceled pickups: `300782`; removed item entities: `122706`.
- Vault block breaks: `201164`; canceled block breaks: `10147`; max per-player block-break burst: `577`.
- Wolds filter handler calls: `180793`; `stackMatchesFilter`: `91022`; `getInventory`: `91022`.
- Virtual-world samples: `462`; max sampled world tick: `230.007 ms`; max hostile mobs: `851`; max item entities: `262`; max loaded chunks: `56155`.
- Server log warnings during Spark window: `13767` `WorldGenRegion` block-entity-before-created warnings, `2292` duplicate entity UUID warnings, `61` Vault `Placing ERROR Block` lines.

VaultSync evidence in latest run:

- FULL delta: `17034`.
- HUD_DIFF delta: `323567`.
- FULL share: `5.001%`.
- HUD_DIFF share: `94.999%`.
- Slow packet outliers exist, including builds over `100 ms`, but the aggregate packet mix remains correct and VaultSync is not the dominant sampled cost.

## Previous Bad Profile Evidence Summary: yTZhEvScpw

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

- Do not modify VaultSync while fixing mapped-vault generation/entity pressure, player/Curios tick cost, Wolds filter cleanup, or hammer-adjacent work.
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
- Downloaded live logs confirm diagnostics ran during mapped-vault load and wrote `stage0_summary` lines.
- The `Y280i8UbT7` Spark capture did not implicate Stage 0 telemetry as a visible hotspot.
- Downloaded VaultSync logs confirm the packet mix remained stable during the Stage 0 and Spark run.
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
- The downloaded Stage 0 log contains periodic summaries under live load.
- The downloaded VaultSync log stayed at about `95%` HUD diff and `5%` full sync for the Stage 0 run, with no errors or LuckPerms anomalies.

Validation gaps:

- Stage 0 telemetry confirms active Wolds filter matching, hammer/block-break bursts, and virtual-world sampling, but it still needs more per-vault attribution for the generation/entity warnings.
- Existing virtual-world samples are aggregate enough to prove load, not precise enough to identify the exact vault worker entity or generation source.

Regression risks:

- Diagnostic logging can become the new lag source if it logs per event.
- World/entity telemetry can touch hot paths. Keep reads cheap and sampled.
- Timing mixins into Wolds methods can break on Wolds updates. Keep them isolated, optional, and telemetry-only.

## Stage 1: Wolds Filter Necklace Cleanup

Status: pending. Stage 0 telemetry for Wolds filter necklace timing is implemented in MasuCraftFixes, but the Wolds behavior/cache fix itself is not implemented.

Latest Stage 0 note:

- The latest live Stage 0 run captured `180793` Wolds filter handler invocations, `91022` `stackMatchesFilter` calls, and `91022` `getInventory` calls.
- The cache opportunity is real because necklace hashes repeated heavily, but Spark ranked the Wolds handler at only `2.5 ms`, `0.2%` of sampled server-thread time.
- Stage 1 remains low-risk cleanup, especially moving the vault-world check before Curios lookup, but it should not be treated as the primary next TPS fix for the latest workload.

This was the lowest-risk first fix from the older severe profile because the measured hotspot was Wolds-owned and local to one feature. The latest Spark capture demotes it behind mapped-vault generation/entity pressure and player/Curios NBT equality.

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

## Stage 2: Hammer Mining Fanout

Status: pending. Stage 0 block-break and per-player/tick burst telemetry is implemented; hammer-adjacent behavior fixes are not implemented.

Latest Stage 0 note:

- The latest live Stage 0 run ended at `201164` vault block-break events, `10147` canceled block-break events, and a max per-player/tick burst of `577`.
- Top block-break contributors in the final summary were `5hekel@4e51ad50-f0d4-4c15-90f1-e12475bf7ca0=46058`, `Purplish2307@46f599ca-0c93-4759-83b6-aac8f41aa580=34966`, and `Mimo_sm@4e51ad50-f0d4-4c15-90f1-e12475bf7ca0=31368`.
- This supports hammer/mining fanout as an active live workload, but direct Spark server-thread sample share was lower than the older severe profile: `ServerboundPlayerActionPacket` was `63.9 ms`, `4.1%`.

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

## Stage 3: Virtual World Worker Completion

Status: pending. Stage 0 read-only virtual-world sampling is implemented; no virtual-world behavior or threading changes have been made.

Latest Stage 0 note:

- The latest live Stage 0 run captured `462` virtual-world samples.
- The maximum observed world tick sample was `230.007 ms`.
- The run reached `851` hostile mobs, `262` item entities, and `56155` loaded chunks across sampled vault worlds.
- Exact-thread Spark analysis confirms worker-thread attribution: `VirtualWorlds.tickWorld -> ServerLevel.tick` was `888.6 ms`, `5.8%` of the `pool-24-thread (x10)` worker group, with `EntityTickList` at `614.8 ms`, `4.0%`.
- The next useful work is not another generic Spark capture; it is more specific attribution by vault id, entity type, chunk count, and generation/log warning correlation.

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

1. Investigate duplicate entity UUID warnings and `WorldGenRegion` block-entity-before-created warnings first.
2. Add or refine read-only per-vault-world telemetry only where the warning investigation needs more live attribution.
3. Correlate long waits with vault id, entity type, item entities, mobs, chunks, generation, active players, and warning volume.
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

Wolds filter cleanup:

- Wolds filter necklace behavior is unchanged from the player's perspective.
- Cache hit rate is high in repeated pickup scenarios.
- `VFTests.checkFilter` call count is materially lower.
- Wolds filter necklace sample share drops in Spark.

Hammer fanout:

- Hammers remain fully supported.
- Same blocks break or are blocked as before.
- Drops are not duplicated or lost.
- Mining tasks/objectives still count.
- Pickup and item entity pressure decreases.

Virtual-world and mapped-generation work:

- `IntLatch.waitUntil` only decreases if worker duration decreases.
- Per-vault-world telemetry explains the remaining waits.
- No virtual-world race, crash, or lifecycle regression appears.
- Mapped vault generation remains functionally correct.

## Recommended Implementation Order

1. Completed: add low-overhead MasuCraftFixes-only telemetry and validation counters.
2. Completed: build the MasuCraftFixes jar with Stage 0 telemetry.
3. Completed: deploy Stage 0 by updating only the MasuCraftFixes jar on Backend server 1 under `/mnt/data/Test Server/`.
4. Completed: boot Backend server 1 and verify there are no MasuCraftFixes, Stage 0, or Wolds optional telemetry mixin regressions.
5. Completed: profile the mapped-vault workload with all relevant logs and write `/home/masuary/Downloads/Logs/Y280i8UbT7-report.md`.
6. Pending: investigate duplicate entity UUID warnings and `WorldGenRegion` block-entity-before-created warnings in mapped-vault generation/spawn paths.
7. Pending: refine virtual-world read-only telemetry by vault id, entity type, chunk count, and warning correlation if the warning investigation needs more live attribution.
8. Pending: investigate Curios/player equipment NBT equality cost during `ServerPlayer.tick`.
9. Pending: keep VaultSync unchanged except for monitoring; only revisit if a new profile implicates it.
10. Pending: implement Wolds filter necklace cache and cheap short-circuits as low-risk cleanup when a Wolds jar update is available.
11. Pending: decide whether hammer-adjacent item entity reduction is still needed after generation/entity and player-tick findings are addressed.
12. Pending: only after evidence, consider guarded item entity coalescing or narrowly scoped VH compatibility micro-optimizations.

## Explicit Non-Goals

- Do not disable hammers.
- Do not cap hammer size as a first-line fix.
- Do not bypass `ForgeHooks.onLeftClickBlock`.
- Do not patch `IntLatch.waitUntil` or skip virtual-world worker completion.
- Do not change VaultSync while fixing these root causes.
- Do not add armor-stand or visual entity workarounds for unrelated nametag features.
