# Spark/VaultSync Diagnosis: yTZhEvScpw

Inputs:

- `/home/masuary/Downloads/yTZhEvScpw.sparkprofile`
- `/home/masuary/Downloads/masucraftfixes-vaultsync.log`
- Repo implementation at `/mnt/data/MasuCraft Mods/MasuCraftFixes/src/main/java/com/masuary/masucraftfixes`
- Wolds source inspected at `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod`

## Verdict

The VaultSync mitigation is working. The remaining low TPS is not caused by `LuckPermsInt` or by per-tick full Vault sync serialization.

The bad TPS in this capture is best explained by active mapped-vault gameplay load:

1. The server thread spends a large share waiting for The Vault virtual-world workers to finish vault-world ticking.
2. Those workers are mostly ticking vault-world entities and chunks, not burning time inside the wait method itself.
3. On the server thread, high-volume block breaking through Vault hammer logic is a major hotspot.
4. Item pickup processing is also hot because many item entities trigger Vault pendant voiding, Wolds filter necklace checks, Sophisticated Backpacks, magnets, and KubeJS/Thermal listeners.
5. Mapped vault generation/chunk placement is present and can explain large spikes, but it is not the dominant sustained cost in this sampled window.

The `Object.wait` / `IntLatch.waitUntil` stack is real, but it is a synchronization symptom: the main server thread is blocked until vault virtual-world worker jobs complete.

Gameplay constraint: hammers are a normal Vault Hunters mechanic and can be used in any vault. This report treats hammer mining as supported gameplay load that the server/modpack needs to handle, not as a feature to disable.

## Profile Health

Profile window:

- Started: `2026-06-11 23:47:52` Europe/Amsterdam
- Ended: `2026-06-11 23:50:54` Europe/Amsterdam
- Duration: `181.7 s`
- Ticks: `1572`
- Average TPS: `8.65`
- Sampler mode: `EXECUTION`
- Interval: `2000 us`
- Minecraft/Forge: `1.18.2` / Forge `40.3.11`

TPS windows:

| Window | TPS | Ticks | Median MSPT | Max MSPT | Process CPU | Entities | Chunks |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 29686907 | 10.09 | 571 | 59.64 | 2052.89 | 35.9% | 654 | 78113 |
| 29686908 | 9.10 | 479 | 62.17 | 2052.89 | 37.4% | 1168 | 78113 |
| 29686909 | 8.56 | 522 | 65.69 | 992.84 | 37.6% | 1153 | 78070 |

GC/system:

- G1 young collections: `27`, average `86.5 ms`
- G1 concurrent collections: `14`, average `6.3 ms`
- G1 old collections: `0`
- Heap used: `20435 MB`
- Process CPU stayed around `36-38%`, so this does not look like full machine CPU saturation.

Interpretation note:

- Spark's grouped `times` data should be used primarily as relative sample weight. Absolute wall-time from grouped threads is easy to misread.
- An existing local helper matched `Netty Epoll Server IO` when searching for a thread containing `server`. I used an exact `Server thread` pass for the TPS analysis.

## Active Vault Context

The VaultSync log shows 10 active vault recipients in 5 vault instances during the bad window:

| Vault | Players | Packet Rows |
|---|---:|---:|
| `1b516871-4823-4069-88bd-d04bbe0f3ecd` | 5 | 8330 |
| `e3de3d81-231f-4faa-b9db-52df1eab8e3a` | 2 | 3332 |
| `0011ec89-8bc3-405e-b639-c1d47755cef3` | 1 | 1666 |
| `a1c4f23f-c4ec-451d-90a4-2a035ff6a337` | 1 | 1666 |
| `8f6d12da-fdf9-4a13-b7c6-5ebad296687b` | 1 | 1666 |

The 5-player vault is the one with the large full-sync payloads and is likely the heavy mapped vault.

## Server Thread Hotspots

Exact `Server thread` top paths by sample share:

| Area | Sample Share | Meaning |
|---|---:|---|
| `ServerGamePacketListenerImpl.tick` / player tick path | 25.94% | Player tick work is high; Neruina is just the wrapper, not the cause. |
| `VirtualWorlds.tick -> ThreadPool.awaitCompletion -> IntLatch.waitUntil` | 24.16% | Main thread waiting for Vault virtual-world workers to finish. |
| `ServerboundPlayerActionPacket -> handlePlayerAction -> handleBlockBreakAction` | 17.78% | Block breaking / hammer mining packet processing. |
| `ForgeEventFactory.onPostServerTick` | 13.85% | Global server tick listeners, including Lootr and Vault card/deck tasks. |
| `Player.touch -> ItemEntity.playerTouch -> ForgeEventFactory.onItemPickup` | 8.38% / 6.42% | Item pickup event pipeline from many drops/entities. |
| `ServerChunkCache` main-thread task drain | 5.70% | Chunk task completion/drain work. |

## Root Cause 1: Hammer Mining Fanout

The strongest direct gameplay hotspot is Vault tool hammer mining.

This does not mean hammers are invalid gameplay. It means hammers expose the expensive path most clearly: one player action becomes many block candidates, event posts, block-state reads, block-break hooks, drops, and later pickup events.

Profile path:

- `ServerboundPlayerActionPacket.m_5797_ -> ServerGamePacketListenerImpl.m_7502_`: `17.78%`
- `ServerPlayerGameMode.handler$enp000$handleBlockBreakAction`: `16.25%`
- `ArrayList.removeIf` over hammer tiles while breaking blocks: `10.94%`
- `ServerPlayerGameMode.computeHammerTiles`: `5.18%`
- `ToolItem.getHammerPositions`: `5.15%`
- `ForgeHooks.onLeftClickBlock` inside hammer-position iteration: `5.13%`

Why it hurts:

- Vault's `ToolItem.getHammerPositions` reads hammer size and iterates a square around the target block.
- For each candidate position, it reads block state and fires `ForgeHooks.onLeftClickBlock`.
- The Vault mixin then tracks/breaks those hammer tiles, which triggers normal block break hooks and listeners for many blocks.
- With large hammer sizes, this scales badly because one player action becomes many event posts and block breaks.

Practical meaning:

- Do not treat “disable hammers” as the fix.
- Treat this as an amplification point: any expensive listener attached to left-click, block-break, drops, item spawning, pickup, magnet, pouch, or filter logic is multiplied by hammer use.
- Fixes should target the multiplied work around hammers, especially pickup filtering and item entity volume.

Source confirmations:

- `ToolItem.getHammerPositions`: `/home/masuary/.claude/docs/mods/Forge/decompiled/the_vault-1.18.2-3.21.5.6573-decompiled/iskallia/vault/item/tool/ToolItem.java:532`
- Vault hammer mixin: `/home/masuary/.claude/docs/mods/Forge/decompiled/the_vault-1.18.2-3.21.5.6573-decompiled/iskallia/vault/mixin/MixinServerPlayerGameMode.java:150`
- Vanilla packet handler: `/home/masuary/.claude/docs/minecraft-1.18.2-decompiled/net/minecraft/server/network/ServerGamePacketListenerImpl.java:949`
- Vanilla block break: `/home/masuary/.claude/docs/minecraft-1.18.2-decompiled/net/minecraft/server/level/ServerPlayerGameMode.java:126`

## Root Cause 2: Item Pickup Event Pipeline

The item pickup path is another sustained contributor.

Profile path:

- `Player.touch`: `8.49%`
- `ItemEntity.playerTouch`: `8.38%`
- `ForgeEventFactory.onItemPickup`: `6.42%`

Hot pickup listeners:

| Listener | Sample Share | Work |
|---|---:|---|
| Vault `PlayerEvents.onVaultPendantUse` | 2.15% | Void checks, `ThemeBlockRetriever.shouldVoidItem`, `VoidCrucibleCustomItemConfig.getAllItems`, magnet lookup. |
| Sophisticated Backpacks `onItemPickup` | 2.12% | Scans/runs backpack inventory logic. |
| Wolds `PlayerEvents.onFilterNecklaceUse` | 1.30% | Curios necklace lookup and filter checks. |
| Vault magnet pickup/durability path | 1.54% | Magnet lookup, gear data reads, durability calculations. |
| KubeJS pickup event | 0.30% | Script event dispatch. |
| Thermal pickup event | 0.19% | Thermal listener. |

Wolds filter necklace source confirms the per-pickup pattern:

- `onFilterNecklaceUse` runs on every `EntityItemPickupEvent` for server players.
- It calls `FilterNecklaceItem.getNecklace(player)` through Curios.
- It checks `ServerVaults.get(world).isPresent()`.
- It calls `stackMatchesFilter`.
- `stackMatchesFilter` reconstructs the necklace inventory from NBT and loops through up to 9 slots.
- Filter slots call `VFTests.checkFilter(stack, slotStack, true, null)`.

Source confirmations:

- Wolds pickup event: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod/src/main/java/xyz/iwolfking/woldsvaults/events/PlayerEvents.java:53`
- Wolds filter check: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod/src/main/java/xyz/iwolfking/woldsvaults/items/filter_necklace/FilterNecklaceItem.java:77`
- Vanilla item pickup event call: `/home/masuary/.claude/docs/minecraft-1.18.2-decompiled/net/minecraft/world/entity/item/ItemEntity.java:332`
- Vanilla player entity touch: `/home/masuary/.claude/docs/minecraft-1.18.2-decompiled/net/minecraft/world/entity/player/Player.java:611`

This is not the only cause, but it is a clear Wolds-specific contributor during high item-volume vault play.

## Root Cause 3: Virtual World Worker Completion

The stack the user noticed is important:

```text
VirtualWorlds.tick()
ThreadPool.awaitCompletion()
ThreadPool.waitFor()
IntLatch.waitUntil()
Object.wait()
```

Interpretation:

- The server thread is waiting for Vault virtual-world tick jobs.
- The wait frame is not the expensive logic by itself.
- The expensive work is on the worker threads that must complete before the latch opens.

Worker-side evidence from `pool-24-thread (x10)`:

| Worker Area | Share of worker group | Meaning |
|---|---:|---|
| `VirtualWorlds.tickWorld` | 4.43% | Active virtual-world tick jobs. |
| `ServerLevel.tick` inside virtual world | 4.43% | Vault world tick. |
| `EntityTickList.forEach` | 3.08% | Entity ticking dominates the worker job. |
| `Zombie.tick` | 1.54% | Mob pressure. |
| `ItemEntity.tick` | 0.54% | Dropped item pressure. |
| `ServerChunkCache.tick` | 1.07% | Chunk ticking. |

So yes, mapped vaults / Vault virtual worlds are involved, but the direct meaning is: the main thread is blocked waiting for vault-world entity/chunk ticking to finish.

## Mapped Vault Generation

Mapped vault generation is present, but not the dominant sustained cost in this capture.

Relevant sampled paths:

- `Worker-Main -> ChunkMap...VaultGeneration -> DummyChunkGenerator -> GridGenerator.generate -> SectionedTemplate.place`
- `Vault-Gen -> SectionedTemplate.place -> BatchBlockPlacer.placeTiles`

This likely explains the worst max-MSPT spikes, including `2052.89 ms`, especially when new mapped rooms/chunks are generated. It does not explain the full sustained 8-10 TPS by itself in this profile.

Wolds map source confirms maps modify the crystal before runtime:

- `VaultMapItem.applyCrystalRecipe` adds size `(map_tier + 1) * 10`, theme/objective, and map modifiers.
- Layout manipulators can replace/extend crystal layout.
- The Wolds map item itself does not appear as a runtime hot stack here.

Source confirmations:

- Wolds map crystal recipe: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod/src/main/java/xyz/iwolfking/woldsvaults/items/gear/VaultMapItem.java:222`
- Map size addition: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod/src/main/java/xyz/iwolfking/woldsvaults/items/gear/VaultMapItem.java:239`
- Layout modification: `/mnt/data/MasuCraft Mods/Wolds-Vaults-Official-Mod/src/main/java/xyz/iwolfking/woldsvaults/recipes/crystal/LayoutModificationRecipe.java:35`

## Other Sustained Costs

Post server tick:

- `ForgeEventFactory.onPostServerTick`: `13.85%`
- Lootr `TileTicker.serverTick`: `2.65%`
- Vault `ActiveCardTaskHelper.onServerTick`: `1.23%`
- Vault `DeckRecipeTaskData.onServerTick`: `0.64%`

These are secondary but real. They add overhead while the server is already overloaded by active vault play.

## VaultSync Result

The VaultSync changes are working.

Packet rows parsed: `16660`

| Kind | Count | Share |
|---|---:|---:|
| `HUD_DIFF` | 15818 | 94.95% |
| `FULL` | 842 | 5.05% |

Reasons:

| Reason | Count |
|---|---:|
| `hud_tick` | 15818 |
| `periodic` | 797 |
| `modifier_count_change` | 45 |

Payload/build comparison:

| Kind | Count | Median Bytes | Avg Bytes | Max Bytes | Median Build | Avg Build | P95 Build | Max Build |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `HUD_DIFF` | 15818 | 424 | 370.7 | 472 | 0.043 ms | 0.054 ms | 0.137 ms | 1.644 ms |
| `FULL` | 842 | 583904 | 309875.2 | 625096 | 7.440 ms | 5.160 ms | 10.413 ms | 119.746 ms |

Total logged payload:

- `HUD_DIFF`: `5,863,888 bytes`
- `FULL`: `260,914,888 bytes`
- Total actual: `266,778,776 bytes`

If the HUD diff rows had been median full syncs for the same vault, traffic would have been roughly `5.1 GB`, about `19x` the actual logged payload.

The only `>20 ms` build spikes were periodic full baselines:

| Timestamp UTC | Player | Bytes | Build |
|---|---|---:|---:|
| `2026-06-11T21:48:11.623976501Z` | `Mimo_sm` | 586608 | 119.746 ms |
| `2026-06-11T21:49:58.424693872Z` | `xDJFz` | 605432 | 114.675 ms |

No `ERROR`, `WARN`, `Exception`, `failed`, disabled, or LuckPerms anomaly lines were found in the VaultSync log.

## LuckPermsInt

`LuckPermsInt` is not a TPS suspect in this capture.

- LuckPerms is present as `5.4.26`.
- `LuckPermsInt` does not show up as meaningful sampled work.
- The current implementation uses LuckPerms cached permission data.
- The VaultSync log has no LuckPerms errors or warnings.

## Most Likely Cause Chain

The most defensible cause chain is:

1. Multiple active vaults and 10 active-vault players are running, including a likely heavy 5-player mapped vault.
2. Wolds maps/layouts create larger or more targeted Vault crystals, which increases active vault world/chunk/entity pressure.
3. Players are mining/looting in those vaults.
4. Vault hammer mining, as normal gameplay, fans one block action into many candidate positions, event posts, and block breaks.
5. Many item entities then hit the pickup event stack: Vault pendant, Wolds filter necklace, Sophisticated Backpacks, magnets, KubeJS, Thermal.
6. Vault virtual-world workers tick mobs/items/chunks and the server thread waits for them at `IntLatch.waitUntil`.
7. Generation/chunk placement adds spikes when new mapped areas are generated.

## Recommended Verification

Run one controlled follow-up profile during the same kind of mapped vault:

1. Stand still in the mapped vault with no mining/looting for 60-120 seconds.
2. Mine with the normal hammer setup for 60-120 seconds.
3. Mine with the same hammer setup but remove or bypass filter necklace, magnets, backpacks, and pendant voiding if possible.
4. Loot with magnets/filter necklace/backpacks enabled but avoid active mining for 60-120 seconds.
5. Optional diagnostic only: compare a no-hammer mining pass to measure hammer amplification. This is not a production recommendation to disable hammers.

Expected results:

- If standing still is still bad, virtual-world entity/chunk pressure is the main cause.
- If normal hammer mining causes the drop, the hammer-amplified work around block events, drops, and pickup processing is the main cause.
- If looting causes the drop, item pickup listeners and item entity volume are the main cause.
- If only new-room traversal spikes, mapped vault generation/chunk placement is the spike cause.

Suggested Spark captures:

```text
/spark profiler --timeout 180 --thread * --only-ticks-over 80
/spark tickmonitor
/spark healthreport
```

Use whatever exact syntax Spark `1.10.38` accepts on this server, but the key is to capture tick-over-threshold wall behavior, not only broad execution sampling.

## Practical Fix Targets

Highest value operational changes:

1. Keep hammers supported, but reduce the extra work triggered around hammer mining: item entity count, pickup scans, filter checks, and repeated gear/filter NBT reads.
2. Reduce item entity volume from mapped vault looting/mining through drop merging, earlier voiding, or fewer physical item entities.
3. Test without Wolds filter necklace, Vault pendant voiding, magnets, and Sophisticated Backpacks pickup handling to isolate which multiplied listener costs the most.
4. Reduce simultaneous mapped vault player count or avoid stacking several active vaults during peak load if virtual-world worker waits remain high.
5. Pre-generate or reduce rapid new-room traversal if max-MSPT spikes are the visible pain.

Highest value code optimization targets:

- Wolds filter necklace should avoid reconstructing its `ItemStackHandler` and running full `VFTests.checkFilter` work on every pickup for every nearby item. Cache the parsed filter state per necklace NBT/version or short-circuit more aggressively before expensive filter evaluation.
- Vault pendant voiding and magnet lookup paths should avoid repeated expensive config/list/gear-data reads per item pickup where possible.
- Any block-left-click or block-break listener that does full gear/tree/filter work should be reviewed because hammer mining multiplies that listener cost across many candidate blocks.
