package com.masuary.masucraftfixes;

import iskallia.vault.core.data.sync.SyncMode;
import iskallia.vault.core.vault.Vault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class VaultSyncTelemetry {
    private static final Logger LOGGER = LogManager.getLogger("MasuCraftFixes/VaultSync");

    private static final boolean LOG_EACH_SYNC = boolProperty("masucraftfixes.vaultSync.logEachSync", false);
    private static final int SUMMARY_INTERVAL_TICKS = Math.max(20, intProperty("masucraftfixes.vaultSync.summaryIntervalTicks", 600));
    private static final int FULL_WARN_BYTES = Math.max(0, intProperty("masucraftfixes.vaultSync.logFullOverBytes", 524288));
    private static final int HUD_WARN_BYTES = Math.max(0, intProperty("masucraftfixes.vaultSync.logHudDiffOverBytes", 131072));
    private static final long SLOW_WARN_NANOS = Math.max(0L, longProperty("masucraftfixes.vaultSync.logSlowOverMs", 10L) * 1_000_000L);

    private static final ThreadLocal<Long> CONSTRUCTION_START_NANOS = new ThreadLocal<>();
    private static final ThreadLocal<SyncMarker> NEXT_MARKER = new ThreadLocal<>();

    private static final SyncStats FULL = new SyncStats();
    private static final SyncStats HUD_DIFF = new SyncStats();
    private static final SyncStats OTHER = new SyncStats();
    private static final LongAdder OFFWORLD_SKIPS = new LongAdder();
    private static final LongAdder STATE_RESETS = new LongAdder();
    private static final ConcurrentHashMap<String, LongAdder> FULL_REASONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> HUD_REASONS = new ConcurrentHashMap<>();
    private static final AtomicInteger LAST_SUMMARY_TICK = new AtomicInteger();

    private VaultSyncTelemetry() {
    }

    public static void startConstructionTimer() {
        CONSTRUCTION_START_NANOS.set(System.nanoTime());
    }

    public static long finishConstructionTimer() {
        Long start = CONSTRUCTION_START_NANOS.get();
        CONSTRUCTION_START_NANOS.remove();
        return start == null ? -1L : System.nanoTime() - start;
    }

    public static void markFullSync(ServerPlayer player, Vault vault, String reason) {
        NEXT_MARKER.set(new SyncMarker(SyncKind.FULL, normalizeReason(reason)));
        startConstructionTimer();
    }

    public static void markHudDiffSync(ServerPlayer player, Vault vault, String reason) {
        NEXT_MARKER.set(new SyncMarker(SyncKind.HUD_DIFF, normalizeReason(reason)));
        startConstructionTimer();
    }

    public static void recordSync(ServerPlayer player, Vault vault, SyncMode mode, int bytes, long nanos) {
        SyncMarker marker = NEXT_MARKER.get();
        NEXT_MARKER.remove();

        SyncKind kind = classify(mode, marker);
        String reason = marker == null ? "unmarked" : marker.reason;

        if (kind == SyncKind.FULL) {
            FULL.record(bytes, nanos);
            increment(FULL_REASONS, reason);
        } else if (kind == SyncKind.HUD_DIFF) {
            HUD_DIFF.record(bytes, nanos);
            increment(HUD_REASONS, reason);
        } else {
            OTHER.record(bytes, nanos);
        }

        if (LOG_EACH_SYNC || shouldWarn(kind, bytes, nanos)) {
            LOGGER.info(
                "vault_sync_packet kind={} mode={} reason={} player={} vault={} payloadBytes={} buildMs={}",
                kind.name(),
                mode,
                reason,
                player.getName().getString(),
                vaultId(vault),
                bytes,
                formatMillis(nanos)
            );
        }

        maybeLogSummary(player);
    }

    public static void recordOffworldSkip(ServerPlayer player, Vault vault, ResourceKey<Level> playerDimension, ResourceKey<Level> vaultDimension) {
        OFFWORLD_SKIPS.increment();
        LOGGER.debug(
            "vault_sync_offworld_skip player={} vault={} playerDim={} vaultDim={}",
            player.getName().getString(),
            vaultId(vault),
            playerDimension.location(),
            vaultDimension.location()
        );
        maybeLogSummary(player);
    }

    public static void recordStateReset(UUID playerId, UUID vaultId, String reason, int removed) {
        STATE_RESETS.add(removed);
        LOGGER.debug("vault_sync_state_reset player={} vault={} reason={} removed={}", playerId, vaultId, reason, removed);
    }

    private static SyncKind classify(SyncMode mode, SyncMarker marker) {
        if (marker != null) {
            return marker.kind;
        }

        if (mode == SyncMode.FULL) {
            return SyncKind.FULL;
        }
        if (mode == SyncMode.DIFF) {
            return SyncKind.OTHER;
        }
        return SyncKind.OTHER;
    }

    private static boolean shouldWarn(SyncKind kind, int bytes, long nanos) {
        if (kind == SyncKind.FULL && FULL_WARN_BYTES > 0 && bytes >= FULL_WARN_BYTES) {
            return true;
        }
        if (kind == SyncKind.HUD_DIFF && HUD_WARN_BYTES > 0 && bytes >= HUD_WARN_BYTES) {
            return true;
        }
        return SLOW_WARN_NANOS > 0 && nanos >= SLOW_WARN_NANOS;
    }

    private static void maybeLogSummary(ServerPlayer player) {
        if (player.server == null) {
            return;
        }

        int tick = player.server.getTickCount();
        int last = LAST_SUMMARY_TICK.get();
        if (tick - last < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        if (!LAST_SUMMARY_TICK.compareAndSet(last, tick)) {
            return;
        }

        LOGGER.info(
            "vault_sync_summary tick={} full={} hudDiff={} other={} offworldSkips={} stateResets={} fullReasons={} hudReasons={}",
            tick,
            FULL.summary(),
            HUD_DIFF.summary(),
            OTHER.summary(),
            OFFWORLD_SKIPS.sum(),
            STATE_RESETS.sum(),
            reasonSummary(FULL_REASONS),
            reasonSummary(HUD_REASONS)
        );
    }

    private static String reasonSummary(ConcurrentHashMap<String, LongAdder> reasons) {
        if (reasons.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, LongAdder> entry : reasons.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue().sum());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static void increment(ConcurrentHashMap<String, LongAdder> map, String reason) {
        map.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "unknown" : reason;
    }

    private static String vaultId(Vault vault) {
        return vault != null && vault.has(Vault.ID) ? vault.get(Vault.ID).toString() : "unknown";
    }

    private static String formatMillis(long nanos) {
        if (nanos < 0L) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
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

    private static long longProperty(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum SyncKind {
        FULL,
        HUD_DIFF,
        OTHER
    }

    private static final class SyncMarker {
        private final SyncKind kind;
        private final String reason;

        private SyncMarker(SyncKind kind, String reason) {
            this.kind = kind;
            this.reason = reason;
        }
    }

    private static final class SyncStats {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalBytes = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxBytes = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();

        private void record(int bytes, long nanos) {
            this.count.increment();
            this.totalBytes.add(bytes);
            if (nanos > 0L) {
                this.totalNanos.add(nanos);
            }
            updateMax(this.maxBytes, bytes);
            updateMax(this.maxNanos, Math.max(0L, nanos));
        }

        private String summary() {
            long countValue = this.count.sum();
            long avgBytes = countValue == 0L ? 0L : this.totalBytes.sum() / countValue;
            double avgMs = countValue == 0L ? 0.0D : (this.totalNanos.sum() / 1_000_000.0D) / countValue;
            return "{count=" + countValue
                + ", avgBytes=" + avgBytes
                + ", maxBytes=" + this.maxBytes.get()
                + ", avgMs=" + String.format(Locale.ROOT, "%.3f", avgMs)
                + ", maxMs=" + String.format(Locale.ROOT, "%.3f", this.maxNanos.get() / 1_000_000.0D)
                + "}";
        }

        private static void updateMax(AtomicLong target, long value) {
            long previous;
            do {
                previous = target.get();
                if (value <= previous) {
                    return;
                }
            } while (!target.compareAndSet(previous, value));
        }
    }
}
