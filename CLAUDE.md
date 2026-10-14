# MasuCraftFixes

Server-side Forge 1.18.2 mod providing fixes for a Vault Hunters server.

## Build

Requires Java 17 (the system default may be Java 21):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-<version>.jar` (current: `1.8.0`).

## Package Structure

- `com.masuary.masucraftfixes` - main mod class, configuration, Vault compatibility, event handler, LuckPerms integration
- `com.masuary.masucraftfixes.mixin` - all mixins

## Mixins

All mixins registered in `src/main/resources/mixins.masucraftfixes.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `AngelExpertiseMixin` | `AngelExpertise` (The Vault) | Prevents angel expertise from stripping FTB `/fly` flight |
| `CommandBlockEditMixin` | `ServerGamePacketListenerImpl` | Restricts command block editing via LuckPerms permission |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `IskalliaDevsMixin` | `IskalliaDevs` (The Vault) | Grants `IskalliaDevs.isDeveloper` based on `masucraftfixes.developer` LuckPerms permission |
| `ModifyCrystalSuggestionsMixin` | `ModifyCrystalSubcommand` (The Vault) | Adds tab-completion suggestions for room pools in `/the_vault modify crystal addRoom` |
| `OrechidIgnemMixin` | `SubTileOrechidIgnem` (Botania) | Allows the Orechid Ignem to operate server-side in Nether-tagged biomes outside ceiling dimensions |
| `PlayerListMixin` | `PlayerList` | Sends a player saved inside a dead vault world to the overworld spawn at login (`AbandonedVaults`) |
| `PickarangMixin` | `Pickarang` (Quark) | Prevents pickup of items tagged fake_item/PreventMagnetMovement |
| `RewardMixin` | `Reward` (The Vault) | Grants reward armor models based on per-model LuckPerms permissions |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |

The one-time build 6574 v1_66 to v1_67 Vault data migration (v1.6.0) was removed
in v1.7.0. A server that ran it has `world/data/masucraftfixes-v166-to-v167.properties`;
its byte-verified originals remain under `world/data/masucraftfixes-v166-backup`.

The Orechid Ignem mixin preserves Botania's ceiling-dimension behavior and also
allows operation when the biome at the flower's exact block position is in
`#minecraft:is_nether`. The added behavior is server-side only; clients receive
Botania's normal block, sound, and mana synchronization and do not need
MasuCraftFixes installed.

## Abandoned Vault Worlds

Vault Hunters keeps a vault world (`the_vault:vault_<uuid>`, listed in
`the_vault_VirtualWorlds.dat`) loaded until its vault ends, and a vault waits for its
players indefinitely. A Vault Hunters update that cannot read `the_vault_Vaults.dat`
(6872 to 6938) leaves every such world without a vault. `AbandonedVaults.cleanUp` runs on
`ServerStartedEvent`:
- closes a running vault through `VaultUtils.closeVault` when every listener is offline
  and their `world/playerdata/<uuid>.dat` is older than `ABANDONED_AFTER` (7 days);
- marks every vault world without a running vault for deletion, so Vault Hunters'
  own `tickDeletions` removes the registry entry and folder one per tick;
- deletes `world/dimensions/the_vault/vault_<uuid>/` folders that are not registered.
Only `vault_<uuid>` names are touched; `vault`, `the_other_side` and `arena` are permanent.

`PlayerListMixin` modifies the stored dimension key in `placeNewPlayer`: a player whose
saved world is a vault world that is missing, marked for deletion or has no running vault
is placed at the overworld shared spawn instead of vanilla's fallback (overworld at the
vault coordinates).

Local QA: `./gradlew vaultCleanupQaJar` builds `masucraftfixes-vault-cleanup-local-qa.jar`
(`/mcfqa status`, `/mcfqa login <uuid>` through the real `placeNewPlayer`). It is a
separate source set, never part of the release jar, and only for disposable world copies.

## LuckPerms Player Checks

Player-entity permission checks go through `LuckPermsInt.permissionIfAvailable`,
which returns empty instead of throwing when LuckPerms has no data for the
entity. Vanilla removes a player 20 ticks after death and Forge then
invalidates the LuckPerms capability until respawn, so the player adapter
throws "Capability missing" for anyone on the death screen. Patron updates for
a removed player are rescheduled until they respawn, an unavailable check never
revokes perks, and every patron update is isolated so a failure is logged
instead of stopping the server tick loop. Login limit checks keep their
allow-on-unavailable behavior; command-block edits fail closed.

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-20.0.3-remastered.6938.jar` - The Vault mod, VH Remastered 3.0.0 (mixin targets)

Remote (via CurseMaven):
- Botania 1.18.2-435 (compile-only mixin target)
- FTB Essentials, Quark, AutoRegLib, LuckPerms
