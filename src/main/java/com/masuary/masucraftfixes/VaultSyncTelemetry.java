package com.masuary.masucraftfixes;

import iskallia.vault.core.data.sync.SyncMode;
import iskallia.vault.core.vault.Vault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class VaultSyncTelemetry {
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
    private static final AtomicBoolean LOG_WRITE_FAILURE_REPORTED = new AtomicBoolean();

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

        if (VaultSyncConfig.isTelemetryEnabled() && (VaultSyncConfig.logEachSync() || shouldWarn(kind, bytes, nanos))) {
            writeTelemetry(
                "vault_sync_packet kind=" + kind.name()
                    + " mode=" + mode
                    + " reason=" + reason
                    + " player=" + player.getName().getString()
                    + " vault=" + vaultId(vault)
                    + " payloadBytes=" + bytes
                    + " buildMs=" + formatMillis(nanos)
            );
        }

        maybeLogSummary(player);
    }

    public static void recordOffworldSkip(ServerPlayer player, Vault vault, ResourceKey<Level> playerDimension, ResourceKey<Level> vaultDimension) {
        OFFWORLD_SKIPS.increment();
        if (VaultSyncConfig.isTelemetryEnabled()) {
            writeTelemetry(
                "vault_sync_offworld_skip player=" + player.getName().getString()
                    + " vault=" + vaultId(vault)
                    + " playerDim=" + playerDimension.location()
                    + " vaultDim=" + vaultDimension.location()
            );
        }
        maybeLogSummary(player);
    }

    public static void recordStateReset(UUID playerId, UUID vaultId, String reason, int removed) {
        STATE_RESETS.add(removed);
        if (VaultSyncConfig.isTelemetryEnabled()) {
            writeTelemetry(
                "vault_sync_state_reset player=" + playerId
                    + " vault=" + vaultId
                    + " reason=" + reason
                    + " removed=" + removed
            );
        }
    }

    public static String currentSummary() {
        return "full=" + FULL.summary()
            + " hudDiff=" + HUD_DIFF.summary()
            + " other=" + OTHER.summary()
            + " offworldSkips=" + OFFWORLD_SKIPS.sum()
            + " stateResets=" + STATE_RESETS.sum()
            + " fullReasons=" + reasonSummary(FULL_REASONS)
            + " hudReasons=" + reasonSummary(HUD_REASONS);
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
        if (kind == SyncKind.FULL && VaultSyncConfig.fullWarnBytes() > 0 && bytes >= VaultSyncConfig.fullWarnBytes()) {
            return true;
        }
        if (kind == SyncKind.HUD_DIFF && VaultSyncConfig.hudWarnBytes() > 0 && bytes >= VaultSyncConfig.hudWarnBytes()) {
            return true;
        }
        long slowWarnNanos = VaultSyncConfig.slowWarnNanos();
        return slowWarnNanos > 0L && nanos >= slowWarnNanos;
    }

    private static void maybeLogSummary(ServerPlayer player) {
        if (!VaultSyncConfig.isTelemetryEnabled()) {
            return;
        }

        if (player.server == null) {
            return;
        }

        int tick = player.server.getTickCount();
        int last = LAST_SUMMARY_TICK.get();
        if (tick - last < VaultSyncConfig.summaryIntervalTicks()) {
            return;
        }

        if (!LAST_SUMMARY_TICK.compareAndSet(last, tick)) {
            return;
        }

        writeTelemetry(
            "vault_sync_summary tick=" + tick + " " + currentSummary()
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

    private static synchronized void writeTelemetry(String message) {
        String line = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
            + " " + message
            + System.lineSeparator();

        try {
            Files.createDirectories(VaultSyncConfig.telemetryLogPath().getParent());
            Files.writeString(
                VaultSyncConfig.telemetryLogPath(),
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            if (LOG_WRITE_FAILURE_REPORTED.compareAndSet(false, true)) {
                MasuCraftFixes.LOGGER.warn("[VaultSync] Could not write telemetry log {}", VaultSyncConfig.telemetryLogPath(), exception);
            }
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
