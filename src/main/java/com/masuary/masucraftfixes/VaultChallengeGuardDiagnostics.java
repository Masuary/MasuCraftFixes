package com.masuary.masucraftfixes;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class VaultChallengeGuardDiagnostics {
    private static final long SUMMARY_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAXIMUM_DETAILS_PER_INTERVAL = 100;

    private final Consumer<String> output;
    private final LongSupplier nanoTime;
    private final boolean enabled;
    private long previousSummaryNanos;
    private long checkedCallbacks;
    private long allowedCallbacks;
    private long blockedCallbacks;
    private long blockedChallenges;
    private long detachedBlockedChallenges;
    private long suppressedDetails;
    private int detailedEvents;

    // All calls are made on the server thread. No per-world map or per-tick file writes.
    VaultChallengeGuardDiagnostics(Consumer<String> output, LongSupplier nanoTime,
                                   String modVersion, String vaultVersion) {
        this.output = output;
        this.nanoTime = nanoTime;
        enabled = VaultChallengeTickGuard.TARGET_VAULT_VERSION.equals(vaultVersion);
        previousSummaryNanos = nanoTime.getAsLong();
        output.accept("event=START state=" + state() + " modVersion=" + modVersion
                + " vaultVersion=" + vaultVersion + " expectedVaultVersion="
                + VaultChallengeTickGuard.TARGET_VAULT_VERSION
                + " callbackObserved=false summarySeconds=60 maxDetailsPerSummary=100");
    }

    void recordCallback(boolean allowed) {
        checkedCallbacks++;
        if (allowed) {
            allowedCallbacks++;
        } else {
            blockedCallbacks++;
        }
        if (checkedCallbacks == 1) {
            output.accept("event=CALLBACK_OBSERVED firstResult=" + (allowed ? "ALLOWED" : "BLOCKED"));
        }
    }

    void recordBlockedChallenge(String details) {
        blockedChallenges++;
        writeDetail("BLOCKED", details);
    }

    void recordDetachedChallenge(String details) {
        detachedBlockedChallenges++;
        writeDetail("DETACHED", details);
    }

    void tick() {
        long currentNanos = nanoTime.getAsLong();
        if (currentNanos - previousSummaryNanos >= SUMMARY_INTERVAL_NANOS) {
            writeSummary("SUMMARY");
            previousSummaryNanos = currentNanos;
            detailedEvents = 0;
            suppressedDetails = 0;
        }
    }

    void stop() {
        writeSummary("STOP");
    }

    private void writeDetail(String event, String details) {
        if (detailedEvents < MAXIMUM_DETAILS_PER_INTERVAL) {
            detailedEvents++;
            output.accept("event=" + event + " " + details);
        } else {
            suppressedDetails++;
        }
    }

    private void writeSummary(String event) {
        output.accept("event=" + event + " state=" + state() + " callbackObserved=" + (checkedCallbacks > 0)
                + " checkedCallbacks=" + checkedCallbacks + " allowedCallbacks=" + allowedCallbacks
                + " blockedCallbacks=" + blockedCallbacks + " blockedChallenges=" + blockedChallenges
                + " detachedBlockedChallenges=" + detachedBlockedChallenges
                + " detailsSuppressedSinceSummary=" + suppressedDetails);
    }

    private String state() {
        return enabled ? "ENABLED" : "DISABLED_VERSION_MISMATCH";
    }
}
