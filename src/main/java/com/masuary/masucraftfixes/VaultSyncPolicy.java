package com.masuary.masucraftfixes;

import iskallia.vault.core.data.key.FieldKey;
import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.player.Listener;
import iskallia.vault.core.world.storage.VirtualWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class VaultSyncPolicy {
    private static final boolean OFFWORLD_GUARD_ENABLED = boolProperty("masucraftfixes.vaultSync.offworldGuard", true);
    private static final boolean HUD_DIFF_ENABLED = boolProperty("masucraftfixes.vaultSync.hudDiffEnabled", true);
    private static final boolean FORCE_FULL_ON_MODIFIER_COUNT_CHANGE = boolProperty("masucraftfixes.vaultSync.forceFullOnModifierCountChange", true);
    private static final int FULL_REFRESH_INTERVAL_TICKS = Math.max(1, intProperty("masucraftfixes.vaultSync.fullRefreshIntervalTicks", 20));
    private static final int STATE_EXPIRY_TICKS = Math.max(1200, intProperty("masucraftfixes.vaultSync.stateExpiryTicks", 12000));
    private static final int CLEANUP_INTERVAL_TICKS = Math.max(200, intProperty("masucraftfixes.vaultSync.cleanupIntervalTicks", 1200));

    private static final ConcurrentHashMap<PlayerVaultKey, SyncState> STATES = new ConcurrentHashMap<>();
    private static final AtomicInteger LAST_CLEANUP_TICK = new AtomicInteger();

    private VaultSyncPolicy() {
    }

    public static boolean shouldSkipOffworld(Vault vault, VirtualWorld world, ServerPlayer player) {
        if (!OFFWORLD_GUARD_ENABLED || world == null || player == null || player.level == null) {
            return false;
        }

        ResourceKey<Level> playerDimension = player.level.dimension();
        ResourceKey<Level> vaultDimension = world.dimension();
        if (playerDimension.equals(vaultDimension)) {
            return false;
        }

        if (vault.has(Vault.ID)) {
            SyncState removed = STATES.remove(new PlayerVaultKey(player.getUUID(), vault.get(Vault.ID)));
            if (removed != null) {
                VaultSyncTelemetry.recordStateReset(player.getUUID(), vault.get(Vault.ID), "offworld_skip", 1);
            }
        }
        VaultSyncTelemetry.recordOffworldSkip(player, vault, playerDimension, vaultDimension);
        return true;
    }

    public static boolean shouldSendVanillaFull(Vault vault, ServerPlayer player) {
        int tick = currentServerTick(player);
        cleanupIfNeeded(tick);

        if (!vault.has(Vault.ID)) {
            VaultSyncTelemetry.markFullSync(player, vault, "missing_vault_id");
            return true;
        }

        PlayerVaultKey key = new PlayerVaultKey(player.getUUID(), vault.get(Vault.ID));
        SyncState state = STATES.computeIfAbsent(key, ignored -> new SyncState());
        state.lastSeenTick = tick;

        int modifierCount = modifierCount(vault);
        boolean finished = vault.has(Vault.FINISHED);

        if (!HUD_DIFF_ENABLED) {
            markFull(state, player, vault, tick, modifierCount, finished, "hud_diff_disabled");
            return true;
        }

        if (!vault.has(Vault.VERSION)) {
            markFull(state, player, vault, tick, modifierCount, finished, "missing_vault_version");
            return true;
        }

        if (!state.seenFull) {
            markFull(state, player, vault, tick, modifierCount, finished, "first_sync");
            return true;
        }

        if (finished && !state.finishedFullSent) {
            markFull(state, player, vault, tick, modifierCount, true, "finished");
            return true;
        }

        if (FORCE_FULL_ON_MODIFIER_COUNT_CHANGE && state.modifierCount != modifierCount) {
            markFull(state, player, vault, tick, modifierCount, finished, "modifier_count_change");
            return true;
        }

        if (tick >= state.nextPeriodicFullTick) {
            markFull(state, player, vault, tick, modifierCount, finished, "periodic");
            return true;
        }

        VaultSyncTelemetry.markHudDiffSync(player, vault, "hud_tick");
        return false;
    }

    public static Vault createHudDiffVault(Vault vault) {
        Vault hud = new Vault();
        hud.set(Vault.VERSION, vault.get(Vault.VERSION));
        hud.set(Vault.ID, vault.get(Vault.ID));

        copyIfPresent(vault, hud, Vault.LEVEL);
        copyIfPresent(vault, hud, Vault.CLOCK);
        copyIfPresent(vault, hud, Vault.LISTENERS);
        copyIfPresent(vault, hud, Vault.OBJECTIVES);
        copyIfPresent(vault, hud, Vault.OVERLAY);
        copyIfPresent(vault, hud, Vault.COMPANION_EGG_HUNT);
        copyIfPresent(vault, hud, Vault.SOUND);

        if (vault.has(Vault.FINISHED)) {
            hud.set(Vault.FINISHED);
        }

        return hud;
    }

    public static void onListenerJoin(Listener listener, Vault vault) {
        clearListenerState(listener, vault, "listener_join");
    }

    public static void onListenerLeave(Listener listener, Vault vault) {
        clearListenerState(listener, vault, "listener_leave");
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            clearPlayer(player.getUUID(), "player_logout");
        }
    }

    private static void clearListenerState(Listener listener, Vault vault, String reason) {
        if (listener == null || vault == null || !vault.has(Vault.ID)) {
            return;
        }

        UUID playerId = listener.getId();
        UUID vaultId = vault.get(Vault.ID);
        SyncState removed = STATES.remove(new PlayerVaultKey(playerId, vaultId));
        if (removed != null) {
            VaultSyncTelemetry.recordStateReset(playerId, vaultId, reason, 1);
        }
    }

    private static void clearPlayer(UUID playerId, String reason) {
        AtomicInteger removed = new AtomicInteger();
        STATES.keySet().removeIf(key -> {
            if (key.playerId.equals(playerId)) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });

        if (removed.get() > 0) {
            VaultSyncTelemetry.recordStateReset(playerId, null, reason, removed.get());
        }
    }

    private static void markFull(SyncState state, ServerPlayer player, Vault vault, int tick, int modifierCount, boolean finished, String reason) {
        state.seenFull = true;
        state.modifierCount = modifierCount;
        state.finishedFullSent = finished;
        state.nextPeriodicFullTick = nextPeriodicFullTick(tick, player.getUUID(), vault.get(Vault.ID));
        VaultSyncTelemetry.markFullSync(player, vault, reason);
    }

    private static int nextPeriodicFullTick(int tick, UUID playerId, UUID vaultId) {
        int earliest = tick + FULL_REFRESH_INTERVAL_TICKS;
        int offset = Math.floorMod(playerId.hashCode() ^ vaultId.hashCode(), FULL_REFRESH_INTERVAL_TICKS);
        int remainder = Math.floorMod(earliest - offset, FULL_REFRESH_INTERVAL_TICKS);
        return remainder == 0 ? earliest : earliest + (FULL_REFRESH_INTERVAL_TICKS - remainder);
    }

    private static int modifierCount(Vault vault) {
        if (!vault.has(Vault.MODIFIERS)) {
            return 0;
        }

        Modifiers modifiers = vault.get(Vault.MODIFIERS);
        return modifiers.getEntries().size();
    }

    private static int currentServerTick(ServerPlayer player) {
        return player.server == null ? 0 : player.server.getTickCount();
    }

    private static void cleanupIfNeeded(int tick) {
        int lastCleanupTick = LAST_CLEANUP_TICK.get();
        if (tick - lastCleanupTick < CLEANUP_INTERVAL_TICKS) {
            return;
        }

        if (!LAST_CLEANUP_TICK.compareAndSet(lastCleanupTick, tick)) {
            return;
        }

        STATES.entrySet().removeIf(entry -> tick - entry.getValue().lastSeenTick > STATE_EXPIRY_TICKS);
    }

    private static <T> void copyIfPresent(Vault source, Vault target, FieldKey<T> field) {
        source.getOptional(field).ifPresent(value -> target.set(field, value));
    }

    private static boolean boolProperty(String key, boolean fallback) {
        String value = System.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class SyncState {
        private boolean seenFull;
        private boolean finishedFullSent;
        private int lastSeenTick;
        private int nextPeriodicFullTick;
        private int modifierCount;
    }

    private static final class PlayerVaultKey {
        private final UUID playerId;
        private final UUID vaultId;

        private PlayerVaultKey(UUID playerId, UUID vaultId) {
            this.playerId = playerId;
            this.vaultId = vaultId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PlayerVaultKey other)) {
                return false;
            }
            return Objects.equals(this.playerId, other.playerId) && Objects.equals(this.vaultId, other.vaultId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.playerId, this.vaultId);
        }
    }
}
