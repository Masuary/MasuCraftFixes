# MasuCraftFixes

Server-side Forge 1.18.2 mod with fixes for the MasuCraft Skyvault (VH Remastered 4.0.1, the_vault 7226) network.
Since 1.9.0 it also contains the Refined Storage chunk fix that used to be the separate RSChunkFix mod (archived).

## Build

Requires Java 17 (the system default may be Java 21):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-<version>.jar` (current: `1.9.2`). javac runs with `-Xlint:all -Werror`;
`./gradlew build` also runs the JUnit tests (`src/test`).

Every mod dependency comes from CurseMaven (file ids in `gradle.properties`, each the exact jar the VH Remastered
4.0.1 pack ships); LuckPerms only through `net.luckperms:api`. Local jars only through an opt-in override, e.g.
`-Pvault_jar=path/to/the_vault.jar` (properties: `vault_jar`, `quark_jar`, `autoreglib_jar`, `ftb_essentials_jar`,
`botania_jar`, `refined_storage_jar`). `deps/` is git-ignored and no longer used by the build.

## Package Structure

- `com.masuary.masucraftfixes` - mod class (registers the event listeners and the command), `MasuCraftFixesMixinPlugin`
  (Botania and Refined Storage mixins apply only when that mod is installed; runs the Vault Integrations check below)
- `command` - `/masucraftfixes status` (op level 2)
- `flight` - FTB `/fly` helpers and `VaultFlightRule`
- `permissions` - `LuckPermsAccess` (node names, player and offline-user checks), `PlayerLimitBypass`
- `vault` - `VaultDimensions`, `DeadVaultRescue`, `CrystalCommandSuggestions`
- `quark` - `QuarkModuleOrder` (Quark modules in class-name order, see `ModuleLoaderMixin`)
- `recipes` - known broken pack recipes: `KnownBrokenPackRecipes` (reads `masucraftfixes/known_broken_pack_recipes.txt`),
  `KnownBrokenRecipeLogFilter` (installed by the mod class), `BrokenRecipeLoadReport` (see below)
- `refinedstorage` - the Refined Storage chunk fix (helpers and duck interfaces of the mixins below)
- `mixin`, `mixin.refinedstorage` - all mixins, named `<Target>Mixin`

## Mixins

All registered in `src/main/resources/mixins.masucraftfixes.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `AngelFlightMixin` | `AngelFlight.revoke` (The Vault 7209+) | While FTB `/fly` is on (outside a vault, or op), VH's Angel revoke only clears its Angel effect and tag, so FTB flight stays (covers the range tick and the expertise regret path) |
| `CommandBlockEditMixin` | `ServerGamePacketListenerImpl` | Command block and minecart edits need `masucraftfixes.commandblock.edit`; checked after the packet moved to the server thread |
| `DedicatedPlayerListMixin` (server) | `DedicatedPlayerList.canBypassPlayerLimit` | `masucraftfixes.ignores_player_limit` lets a player join a full backend (LuckPerms Forge loads users during login negotiation, before this check) |
| `FTBCheatCommandsMixin` | `CheatCommands.fly` (FTB Essentials) | Blocks `/fly` inside vault dimensions for non-ops |
| `ModuleLoaderMixin` | `ModuleLoader.findModules` (Quark) | Quark modules run in class-name order instead of `HashMap<Class>` order, so the unsaved block entity, menu and recipe serializer ids are the same on every backend and boot (a hopping client keeps its first backend's ids under Ambassador's `bypass-registry-checks`) |
| `ModifyCrystalSuggestionsMixin` | `ModifyCrystalSubcommand` (The Vault) | Room pool suggestions for `/the_vault modify crystal addRoom` |
| `OrechidIgnemMixin` | `SubTileOrechidIgnem` (Botania, optional) | Orechid Ignem also works in `#minecraft:is_nether` biomes outside ceiling dimensions |
| `PickarangMixin` | `Pickarang` (Quark) | Pickarang ignores items tagged `fake_item` / `PreventMagnetMovement` |
| `PlayerListMixin` | `PlayerList.placeNewPlayer` | Player saved in a missing, deleted or vault-less vault world goes to their respawn point (world spawn otherwise) and is told why (`DeadVaultRescue`); VH 7209 only rescues from a loaded vault world |
| `RecipeManagerMixin` | `RecipeManager.apply` (TAIL) | Counts the known broken pack recipes and warns about stale list entries (see Known broken pack recipes) |
| `refinedstorage.*` | Refined Storage 1.10.6 (optional) | See below |

`VaultFlightRule` (not a mixin) switches FTB `/fly` off in vault dimensions for non-ops from the server-thread
`PlayerTickEvent` (VH ticks vault worlds, and `ServerPlayer.tick`, on worker threads; FTB's data is not thread-safe).

## Refined Storage chunk fix (`mixin.refinedstorage`, `refinedstorage`)

Stock RS ticks every network and node of a dimension each tick and reads the controller's block, so at server start it
loads every RS chunk. Rules:
- `NetworkMixin`: a network whose controller chunk is unloaded does not update, counts as offline (`amILoaded = false`,
  as in stock RS) and owes a graph rebuild, done by RS's own 20-tick inactive-to-active transition once it loads.
- `NetworkNodeGraphMixin`: a graph rebuild started while the controller chunk is unloaded is postponed the same way.
- `NetworkNodeGraphOperatorMixin`: a rebuild that meets a node of a network whose controller chunk is unloaded takes the
  node over (and drops it from that graph) instead of breaking the block, since that network's rebuilds wait.
- `NetworkListenerMixin` + `NetworkNodeMixin`: nodes in unloaded chunks are not ticked at all (also add-on nodes such
  as Vault Integrations' Altar Conduit); a node whose chunk loads without a network next to a networked node makes that
  network rebuild after 20 quiet ticks (`RefinedStorageReconnects`, keyed by the network's own level).
- `CraftingManagerMixin`: `writeToNbt` also writes saved tasks RS has not read back yet and tasks started this tick
  (RS saves only running tasks, which deleted crafts of offline networks with their ingredients).
- RS is pinned to `[1.10.6]` in `mods.toml`: re-read every target before allowing another RS version. `mods.toml` also
  refuses to load next to `rschunkfix` (both would cancel the same RS methods).
- Vault Integrations 1.0.22+ has its own subset of this fix (`refinedStorageNoForcedChunkLoads`, package
  `mixin.storage.unloadedchunks`); its `@Redirect` of `INetworkNode.update()` in `NetworkListener.onLevelTick` collides
  with ours and fails the boot. Ours is kept (sir, 2026-11-23: carry-over, adoption and reconnects build on it), so every
  backend ships `config/vaultintegrations/storage_fixes.json` with `"refinedStorageNoForcedChunkLoads": {"enabled":
  false}` (VI's other storage fixes stay on). `VaultIntegrationsChunkFixCheck`, called from the mixin plugin when it
  decides `NetworkListenerMixin`, stops the boot with a message naming that file and key while VI's fix is active, and
  also when VI's `StorageFix` no longer has that constant (re-check VI's RS mixins on every VI update).

## Known broken pack recipes (`recipes`)

The VH Remastered 4.0.1 pack ships 105 recipes that cannot load in it (items its mod versions do not have); the stock
pack logs a parse error stack trace for each on every boot and `/reload`. Sir's rule: hide them, never fix them (what
does not work in the base pack stays broken). `KnownBrokenRecipeLogFilter` is a log4j filter on the `RecipeManager`
logger that drops only the "Parsing error loading recipe {}" events of the listed ids; any other recipe error still
logs. It is a log filter and not a redirect because Steam Rails already `@Redirect`s that `Logger.error` call
(priority 10000) and a second redirect fails the boot. `RecipeManagerMixin` (TAIL of `apply`) logs one INFO count and
WARNs for listed recipes that load now or are no longer shipped: rebuild the list from a stock boot log on every pack
update.

## LuckPerms

Node names live in `LuckPermsAccess`. Player-entity checks go through `permissionIfAvailable`, which returns empty
when LuckPerms has no data for the entity (Forge invalidates the LuckPerms capability of a dead player until respawn);
command-block edits fail closed. The player-limit bypass reads the loaded user by UUID and counts as not granted (one
WARN) if LuckPerms has not loaded the user.

## Retired (2026-11-21, phase R)

The LuckPerms patron tiers, reward-transmog permissions, the developer flag and `/donatordisplay` (they handed out
Iskall's Patreon rewards through LuckPerms) and the startup vault cleanup (`AbandonedVaults`, its world-folder deletion
could remove every running vault if VH's registry failed to load). History: `PLAN.md`.

## Testing

Unit tests cover the pure logic (vault dimension parsing, crafting task carry-over, reconnect decision, `DueQueue`,
the Vault Integrations check against stand-in `StorageFix` enums, the plugin's `NetworkListenerMixin` name against
`mixins.masucraftfixes.json`).
Runtime behaviour is checked on the Skyvault U4 test network
(`/mnt/data/AI/work/masucraftfixes-phaseR-2026-11-21/qa/` on 4.0.0, `/mnt/data/AI/work/masucraftfixes-r2a-2026-11-23/qa/`
on 4.0.1: scripts, `testnet-results.md`).

<!-- masuary-os-area --> Masuary OS area: [minecraft](/mnt/data/AI/os/areas/minecraft.md) (where this project sits in the map; update that line when this folder moves)
