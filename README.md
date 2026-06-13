# MasuCraftFixes

Server-side Forge `1.18.2` fixes for the MasuCraft/Wolds Vault Hunters server.

This branch is the Wolds-focused build. It keeps the fixes needed by this server pack and intentionally omits unrelated fixes from the broader MasuCraft branch.

## Build

Requires Java 17.

```bash
./gradlew build
```

Output:

```text
build/libs/masucraftfixes-wolds-<version>.jar
```

Current Gradle artifact name:

```text
masucraftfixes-wolds
```

## Main Features

- VaultSync mitigation for Vault Hunters listener sync spam.
- Stage 0 performance telemetry for mapped-vault diagnostics.
- Vessel anti-AFK safeguards.
- LuckPerms player-limit bypass tag.
- Vault flight prevention for FTB Essentials flight.
- Optional CasinoCraft mixins, only applied when CasinoCraft is loaded.

## VaultSync DIFF Method

Vault Hunters normally builds frequent `VaultMessage.Sync` packets for active vault listeners. This mod intercepts the listener sync path and avoids sending the full Vault object every tick when the client only needs HUD-facing fields.

The flow is:

1. `VaultListenerSyncReplaceMixin` intercepts `Listener.tickServer`.
2. `VaultSyncPolicy.shouldSkipOffworld` cancels stale syncs when the player is no longer in the vault dimension.
3. `VaultSyncPolicy.shouldSendVanillaFull` decides whether vanilla FULL sync is required.
4. If FULL is not required, `VaultSyncPolicy.createHudDiffVault` builds a small HUD-only Vault object.
5. The mod sends `new VaultMessage.Sync(player, hudVault, SyncMode.DIFF)` to the player and cancels the original FULL packet.

The HUD DIFF Vault keeps only fields required for normal HUD updates:

- `VERSION`
- `ID`
- `LEVEL`
- `CLOCK`
- `LISTENERS`
- `OBJECTIVES`
- `OVERLAY`
- `COMPANION_EGG_HUNT`
- `SOUND`
- `FINISHED`, when present

FULL syncs are still sent for safety:

- First sync for a player+vault pair.
- Missing vault id or version.
- Vault finished state.
- Modifier count change.
- Periodic refresh.
- When HUD DIFF is disabled by system property.

State is in memory only, keyed by player UUID and vault UUID. It is reset on listener join, listener leave, player logout, offworld skip, and state expiry. It does not write Vault saved data, Wolds data, crystal NBT, or player inventory.

## VaultSync Controls

Runtime command:

```text
/vaultsync status
/vaultsync telemetry true|false
/vaultsync debug true|false
/vaultsync log-each-sync true|false
/vaultsync summary-interval <ticks>
/vaultsync slow-threshold <ms>
/vaultsync full-refresh-interval <ticks>
```

Config file:

```text
config/masucraftfixes-vaultsync.properties
```

Telemetry log:

```text
logs/masucraftfixes-vaultsync.log
```

Relevant system properties:

```text
-Dmasucraftfixes.vaultSync.offworldGuard=true|false
-Dmasucraftfixes.vaultSync.hudDiffEnabled=true|false
-Dmasucraftfixes.vaultSync.forceFullOnModifierCountChange=true|false
-Dmasucraftfixes.vaultSync.stateExpiryTicks=<ticks>
-Dmasucraftfixes.vaultSync.cleanupIntervalTicks=<ticks>
```

## Stage 0 Telemetry

Stage 0 is read-only diagnostics for mapped-vault performance work. It records aggregate counters for pickup events, block-break bursts, Wolds filter necklace paths, and sampled vault-world pressure.

Runtime command:

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

Config file:

```text
config/masucraftfixes-stage0.properties
```

Telemetry log:

```text
logs/masucraftfixes-stage0.log
```

Optional Wolds telemetry mixin kill switches:

```text
-Dmasucraftfixes.stage0.woldsMixins=false
-Dmasucraftfixes.stage0.woldsPlayerEventsMixin=false
-Dmasucraftfixes.stage0.woldsFilterNecklaceItemMixin=false
```

## Other Commands

```text
/vesselantiafk debug true|false
```

## Important Files

- `src/main/java/com/masuary/masucraftfixes/VaultSyncPolicy.java`
- `src/main/java/com/masuary/masucraftfixes/mixin/VaultListenerSyncReplaceMixin.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncTelemetry.java`
- `src/main/java/com/masuary/masucraftfixes/VaultSyncCommand.java`
- `src/main/java/com/masuary/masucraftfixes/Stage0Telemetry.java`
- `src/main/java/com/masuary/masucraftfixes/Stage0TelemetryEvents.java`
- `src/main/resources/mixins.masucraftfixes.json`

## Dependencies

Local JARs are expected in `deps/`.

Important compile targets:

- Forge `1.18.2-40.3.11`
- The Vault `1.18.2-3.21.5.6573`
- LuckPerms API
- FTB Essentials
- Crafting Tweaks
- Quark and AutoRegLib
