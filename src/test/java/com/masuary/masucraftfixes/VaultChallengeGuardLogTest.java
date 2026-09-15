package com.masuary.masucraftfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VaultChallengeGuardLogTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void realFileIsFlushedImmediatelyAndExistingLogsSurviveReopening() throws IOException {
        Path path = temporaryFolder.getRoot().toPath().resolve("logs/guard.log");
        List<String> failures = new ArrayList<>();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        int rootAppenderCount = configuration.getRootLogger().getAppenders().size();
        try (VaultChallengeGuardLog log = new VaultChallengeGuardLog(path, "5 MB", 3,
                (message, exception) -> failures.add(message))) {
            VaultChallengeGuardDiagnostics diagnostics = new VaultChallengeGuardDiagnostics(log, () -> 0L,
                    "test-build", VaultChallengeTickGuard.TARGET_VAULT_VERSION);
            diagnostics.recordCallback(false);
            diagnostics.recordBlockedChallenge("challenge=test-id dimension=the_vault:test registeredWorldMatches=false");
            diagnostics.recordDetachedChallenge("challenge=test-id attachedWorldCleared=true");
            diagnostics.stop();
            List<String> lines = Files.readAllLines(path);
            assertEquals(5, lines.size());
            OffsetDateTime.parse(lines.get(0).substring(0, lines.get(0).indexOf(' ')));
            assertTrue(lines.get(2).contains("event=BLOCKED challenge=test-id"));
            assertTrue(lines.get(3).contains("event=DETACHED challenge=test-id"));
            assertTrue(lines.get(4).contains("event=STOP"));
        }
        try (VaultChallengeGuardLog log = new VaultChallengeGuardLog(path, "5 MB", 3,
                (message, exception) -> failures.add(message))) {
            log.accept("event=START next-server-session=true");
        }
        assertEquals(6, Files.readAllLines(path).size());
        assertSame(configuration, context.getConfiguration());
        assertEquals(rootAppenderCount, configuration.getRootLogger().getAppenders().size());
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void rotationKeepsOnlyConfiguredArchivesAndTheCurrentFile() throws IOException {
        Path path = temporaryFolder.newFolder("rotation").toPath().resolve("guard.log");
        List<String> failures = new ArrayList<>();
        try (VaultChallengeGuardLog log = new VaultChallengeGuardLog(path, "512", 2,
                (message, exception) -> failures.add(message))) {
            for (int index = 0; index < 100; index++) {
                log.accept("event=BLOCKED challenge=test-" + index + " details=" + "x".repeat(100));
            }
        }
        assertTrue(Files.exists(path));
        assertTrue(Files.exists(path.resolveSibling("guard.log.1")));
        assertTrue(Files.exists(path.resolveSibling("guard.log.2")));
        assertFalse(Files.exists(path.resolveSibling("guard.log.3")));
        try (Stream<Path> files = Files.list(path.getParent())) {
            assertEquals(3, files.count());
        }
        assertTrue(Files.readString(path).contains("challenge=test-99"));
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void failedFileCreationReportsOnceWithoutBreakingGuardDiagnostics() throws IOException {
        Path blockedDirectory = temporaryFolder.newFile("not-a-directory").toPath();
        List<String> failures = new ArrayList<>();
        try (VaultChallengeGuardLog log = new VaultChallengeGuardLog(blockedDirectory.resolve("guard.log"), "5 MB", 3,
                (message, exception) -> failures.add(message))) {
            log.accept("event=BLOCKED challenge=first");
            log.accept("event=BLOCKED challenge=second");
        }
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("disabled until restart"));
        assertTrue(failures.get(0).contains(blockedDirectory.toString()));
        assertTrue(failures.get(0).contains("Safety-guard behavior is unchanged"));
    }

    @Test
    public void newlinesInDetailsCannotForgeExtraLogEntries() throws IOException {
        Path path = temporaryFolder.getRoot().toPath().resolve("guard.log");
        List<String> failures = new ArrayList<>();
        try (VaultChallengeGuardLog log = new VaultChallengeGuardLog(path, "5 MB", 3,
                (message, exception) -> failures.add(message))) {
            log.accept("event=BLOCKED details=first\nsecond\rthird");
        }
        List<String> lines = Files.readAllLines(path);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("first\\nsecond\\rthird"));
        assertTrue(failures.toString(), failures.isEmpty());
    }
}
