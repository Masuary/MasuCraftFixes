# MasuCraftFixes

Server-side Forge 1.18.2 mod providing fixes for a Vault Hunters server.

## Build

Requires Java 17 (the system default may be Java 21):
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-<version>.jar` (current: `1.6.0`).

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
| `PickarangMixin` | `Pickarang` (Quark) | Prevents pickup of items tagged fake_item/PreventMagnetMovement |
| `RewardMixin` | `Reward` (The Vault) | Grants reward armor models based on per-model LuckPerms permissions |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |
| `VaultSnapshotV166CompatibilityMixin` | `VaultSnapshot` (The Vault) | Decodes build 6574 snapshots during the one-time v1_67 disk migration |

The v1_66 compatibility mixin and migration are applied only to The Vault
`1.18.2-20.0.3-remastered.6872`. Before normal world loading, MasuCraftFixes
atomically converts active Vault records and historical snapshots from the
ambiguous build 6574 v1_66 schema to the standard v1_67 schema. Original files
are retained under `world/data/masucraftfixes-v166-backup`, and completion is
recorded in `world/data/masucraftfixes-v166-to-v167.properties`. The temporary
schema reader is removed in the same process before Vault data loads normally.
Later Vault versions skip this compatibility code entirely and read the
standard v1_67 files without MasuCraftFixes migration support.

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-20.0.3-remastered.6872.jar` - The Vault mod (mixin targets)

Remote (via CurseMaven):
- FTB Essentials, Quark, AutoRegLib, LuckPerms
