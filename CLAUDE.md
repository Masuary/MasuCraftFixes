# MasuCraftFixes

Server-side Forge 1.18.2 mod providing fixes for a Vault Hunters server.

## Build

Requires Java 17 (system default is Java 21, so use `JAVA_HOME="C:/Program Files/Java/jdk-17"` when building):
```bash
JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew build
```

Output JAR: `build/libs/masucraftfixes-1.0.0.jar`

## Package Structure

- `com.masuary.masucraftfixes` - main mod class, event handler, LuckPerms integration
- `com.masuary.masucraftfixes.mixin` - all mixins

## Mixins

All mixins registered in `src/main/resources/mixins.masucraftfixes.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `AngelExpertiseMixin` | `AngelExpertise` (The Vault) | Prevents angel expertise from stripping FTB `/fly` flight |
| `CommandBlockEditMixin` | `ServerGamePacketListenerImpl` | Restricts command block editing via LuckPerms permission |
| `FTBCheatCommandsMixin` | `CheatCommands` (FTB Essentials) | Blocks `/fly` inside vault dimensions |
| `PickarangMixin` | `Pickarang` (Quark) | Prevents pickup of items tagged fake_item/PreventMagnetMovement |
| `ModifyCrystalSuggestionsMixin` | `ModifyCrystalSubcommand` (The Vault) | Adds tab-completion suggestions for room pools in `/the_vault modify crystal addRoom` |
| `ServerPlayerMixin` | `ServerPlayer` | Disables active FTB flight inside vault dimensions each tick |

## Dependencies

Local JARs in `deps/`:
- `luckperms-forge.jar` - LuckPerms Forge API
- `the_vault-1.18.2-3.20.3.6055.jar` - The Vault mod (for AngelExpertise mixin target)

Remote (via CurseMaven):
- FTB Essentials, Quark, AutoRegLib, LuckPerms
