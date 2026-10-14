# MasuCraftFixes

Server-side Forge 1.18.2 mod providing fixes for a Vault Hunters server.

## Build

Requires Java 17 (the system default may be Java 21):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-<version>.jar` (current: `1.7.0`).

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
