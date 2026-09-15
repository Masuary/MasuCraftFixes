package com.masuary.masucraftfixes;

import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VaultChallengeGuardDiagnosticsTest {
    @Test
    public void startupAndIdleSummaryDoNotClaimTheCallbackHasBeenObserved() {
        List<String> lines = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        VaultChallengeGuardDiagnostics diagnostics = newDiagnostics(lines, clock);
        assertTrue(lines.get(0).contains("event=START state=ENABLED"));
        assertTrue(lines.get(0).contains("callbackObserved=false"));
        clock.set(Duration.ofSeconds(59).toNanos());
        diagnostics.tick();
        assertEquals(1, lines.size());
        clock.set(Duration.ofMinutes(1).toNanos());
        diagnostics.tick();
        assertTrue(lines.get(1).contains("event=SUMMARY state=ENABLED callbackObserved=false"));
        assertTrue(lines.get(1).contains("checkedCallbacks=0"));
    }

    @Test
    public void callbackBlockAndDetachHaveSeparateEvidenceAndAccurateTotals() {
        List<String> lines = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        VaultChallengeGuardDiagnostics diagnostics = newDiagnostics(lines, clock);
        diagnostics.recordCallback(true);
        diagnostics.recordCallback(false);
        diagnostics.recordBlockedChallenge("challenge=test-id registeredWorldMatches=false");
        diagnostics.recordCallback(false);
        diagnostics.recordDetachedChallenge("challenge=test-id attachedWorldCleared=true");
        diagnostics.stop();
        assertEquals(5, lines.size());
        assertEquals("event=CALLBACK_OBSERVED firstResult=ALLOWED", lines.get(1));
        assertTrue(lines.get(2).startsWith("event=BLOCKED challenge=test-id"));
        assertTrue(lines.get(3).startsWith("event=DETACHED challenge=test-id"));
        assertTrue(lines.get(4).contains("event=STOP state=ENABLED callbackObserved=true"));
        assertTrue(lines.get(4).contains("checkedCallbacks=3 allowedCallbacks=1 blockedCallbacks=2"
                + " blockedChallenges=1 detachedBlockedChallenges=1"));
    }

    @Test
    public void detailFloodIsBoundedWithoutLosingCountersAndResetsEachMinute() {
        List<String> lines = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        VaultChallengeGuardDiagnostics diagnostics = newDiagnostics(lines, clock);
        for (int index = 0; index < 150; index++) {
            diagnostics.recordCallback(false);
            diagnostics.recordBlockedChallenge("challenge=" + index);
            diagnostics.recordDetachedChallenge("challenge=" + index);
        }
        assertEquals(102, lines.size());
        clock.set(Duration.ofMinutes(1).toNanos());
        diagnostics.tick();
        String summary = lines.get(102);
        assertTrue(summary.contains("blockedCallbacks=150 blockedChallenges=150 detachedBlockedChallenges=150"));
        assertTrue(summary.contains("detailsSuppressedSinceSummary=200"));
        diagnostics.recordBlockedChallenge("challenge=next-window");
        assertEquals("event=BLOCKED challenge=next-window", lines.get(103));
    }

    @Test
    public void validCallbacksDoNotWriteALinePerTick() {
        List<String> lines = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        VaultChallengeGuardDiagnostics diagnostics = newDiagnostics(lines, clock);
        for (int index = 0; index < 10000; index++) {
            diagnostics.recordCallback(true);
        }
        assertEquals(2, lines.size());
        diagnostics.stop();
        assertTrue(lines.get(2).contains("checkedCallbacks=10000 allowedCallbacks=10000 blockedCallbacks=0"));
    }

    @Test
    public void unsupportedVaultVersionIsExplicitlyReportedAsDisabled() {
        List<String> lines = new ArrayList<>();
        new VaultChallengeGuardDiagnostics(lines::add, () -> 0L, "test-build", "different-build");
        assertTrue(lines.get(0).contains("state=DISABLED_VERSION_MISMATCH"));
        assertTrue(lines.get(0).contains("vaultVersion=different-build"));
        assertFalse(lines.get(0).contains("state=ENABLED"));
    }

    private static VaultChallengeGuardDiagnostics newDiagnostics(List<String> lines, AtomicLong clock) {
        return new VaultChallengeGuardDiagnostics(lines::add, clock::get,
                "test-build", VaultChallengeTickGuard.TARGET_VAULT_VERSION);
    }
}
