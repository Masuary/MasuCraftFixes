package com.masuary.masucraftfixes;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class VaultChallengeGuardLog implements Consumer<String>, AutoCloseable {
    private final Path logPath;
    private final BiConsumer<String, Throwable> failureReporter;
    private RollingFileAppender appender;
    private boolean failed;

    VaultChallengeGuardLog(Path logPath, String maximumFileSize, int archiveCount,
                           BiConsumer<String, Throwable> failureReporter) {
        this.logPath = logPath;
        this.failureReporter = failureReporter;
        try {
            Files.createDirectories(logPath.toAbsolutePath().getParent());
            Configuration configuration = ((LoggerContext) LogManager.getContext(false)).getConfiguration();
            appender = RollingFileAppender.newBuilder()
                    .setName("MasuCraftVaultChallengeGuard")
                    .setConfiguration(configuration)
                    .setIgnoreExceptions(false)
                    .setLayout(PatternLayout.newBuilder()
                            .withConfiguration(configuration)
                            .withCharset(StandardCharsets.UTF_8)
                            .withPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %msg%n")
                            .build())
                    .withFileName(logPath.toString())
                    .withFilePattern(logPath + ".%i")
                    .withAppend(true)
                    .withImmediateFlush(true)
                    .withPolicy(SizeBasedTriggeringPolicy.createPolicy(maximumFileSize))
                    .withStrategy(DefaultRolloverStrategy.newBuilder()
                            .withConfig(configuration)
                            .withMin("1")
                            .withMax(Integer.toString(archiveCount))
                            .withFileIndex("min")
                            .build())
                    .build();
            if (appender == null) {
                throw new IllegalStateException("Log4j could not open the challenge guard log");
            }
            // This appender is owned here, not attached to or propagated through the root logger.
            appender.start();
        } catch (IOException | RuntimeException exception) {
            fail(exception);
        }
    }

    @Override
    public void accept(String line) {
        if (appender == null || failed) {
            return;
        }
        try {
            appender.append(Log4jLogEvent.newBuilder()
                    .setLoggerName("MasuCraftVaultChallengeGuard")
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage(line.replace("\r", "\\r").replace("\n", "\\n")))
                    .build());
        } catch (RuntimeException exception) {
            fail(exception);
        }
    }

    @Override
    public void close() {
        RollingFileAppender previousAppender = appender;
        appender = null;
        if (previousAppender != null) {
            try {
                previousAppender.stop();
            } catch (RuntimeException exception) {
                fail(exception);
            }
        }
    }

    private void fail(Exception exception) {
        if (!failed) {
            failed = true;
            // Diagnostics are best-effort: a disk failure must never disable the safety guard.
            failureReporter.accept("Challenge guard diagnostic log disabled until restart: " + logPath
                    + ". Safety-guard behavior is unchanged; only diagnostics are unavailable.", exception);
        }
    }
}
