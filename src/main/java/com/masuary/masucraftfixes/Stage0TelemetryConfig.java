package com.masuary.masucraftfixes;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Stage0TelemetryConfig {
    private static final Path CONFIG_PATH = Path.of("config", "masucraftfixes-stage0.properties");
    private static final Path TELEMETRY_LOG_PATH = Path.of("logs", "masucraftfixes-stage0.log");

    private static volatile boolean loaded;
    private static volatile boolean telemetryEnabled;
    private static volatile boolean logSlowEvents;
    private static volatile int summaryIntervalTicks;
    private static volatile int worldSampleIntervalTicks;
    private static volatile int topEntryLimit;
    private static volatile long slowWarnMs;

    private Stage0TelemetryConfig() {
    }

    public static void bootstrap() {
        ensureLoaded();
    }

    public static boolean isTelemetryEnabled() {
        ensureLoaded();
        return telemetryEnabled;
    }

    public static boolean logSlowEvents() {
        ensureLoaded();
        return logSlowEvents;
    }

    public static int summaryIntervalTicks() {
        ensureLoaded();
        return summaryIntervalTicks;
    }

    public static int worldSampleIntervalTicks() {
        ensureLoaded();
        return worldSampleIntervalTicks;
    }

    public static int topEntryLimit() {
        ensureLoaded();
        return topEntryLimit;
    }

    public static long slowWarnNanos() {
        ensureLoaded();
        return Math.max(0L, slowWarnMs) * 1_000_000L;
    }

    public static long slowWarnMs() {
        ensureLoaded();
        return slowWarnMs;
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static Path telemetryLogPath() {
        return TELEMETRY_LOG_PATH;
    }

    public static void setTelemetryEnabled(boolean enabled) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            telemetryEnabled = enabled;
            saveLocked();
        }
    }

    public static void setLogSlowEvents(boolean enabled) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            logSlowEvents = enabled;
            saveLocked();
        }
    }

    public static void setSummaryIntervalTicks(int ticks) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            summaryIntervalTicks = Math.max(20, ticks);
            saveLocked();
        }
    }

    public static void setWorldSampleIntervalTicks(int ticks) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            worldSampleIntervalTicks = Math.max(20, ticks);
            saveLocked();
        }
    }

    public static void setSlowWarnMs(long ms) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            slowWarnMs = Math.max(0L, ms);
            saveLocked();
        }
    }

    public static void setTopEntryLimit(int limit) {
        ensureLoaded();
        synchronized (Stage0TelemetryConfig.class) {
            topEntryLimit = Math.max(1, limit);
            saveLocked();
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (Stage0TelemetryConfig.class) {
            if (loaded) {
                return;
            }

            applyDefaults();
            if (Files.exists(CONFIG_PATH)) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    properties.load(reader);
                    apply(properties);
                } catch (IOException exception) {
                    MasuCraftFixes.LOGGER.warn("[Stage0] Could not load {}, using defaults", CONFIG_PATH, exception);
                }
            } else {
                saveLocked();
            }

            loaded = true;
        }
    }

    private static void applyDefaults() {
        telemetryEnabled = boolProperty("masucraftfixes.stage0.telemetryEnabled", false);
        logSlowEvents = boolProperty("masucraftfixes.stage0.logSlowEvents", false);
        summaryIntervalTicks = Math.max(20, intProperty("masucraftfixes.stage0.summaryIntervalTicks", 600));
        worldSampleIntervalTicks = Math.max(20, intProperty("masucraftfixes.stage0.worldSampleIntervalTicks", 100));
        topEntryLimit = Math.max(1, intProperty("masucraftfixes.stage0.topEntryLimit", 8));
        slowWarnMs = Math.max(0L, longProperty("masucraftfixes.stage0.slowWarnMs", 5L));
    }

    private static void apply(Properties properties) {
        telemetryEnabled = boolValue(properties, "telemetryEnabled", telemetryEnabled);
        logSlowEvents = boolValue(properties, "logSlowEvents", logSlowEvents);
        summaryIntervalTicks = Math.max(20, intValue(properties, "summaryIntervalTicks", summaryIntervalTicks));
        worldSampleIntervalTicks = Math.max(20, intValue(properties, "worldSampleIntervalTicks", worldSampleIntervalTicks));
        topEntryLimit = Math.max(1, intValue(properties, "topEntryLimit", topEntryLimit));
        slowWarnMs = Math.max(0L, longValue(properties, "slowWarnMs", slowWarnMs));
    }

    private static void saveLocked() {
        Properties properties = new Properties();
        properties.setProperty("telemetryEnabled", Boolean.toString(telemetryEnabled));
        properties.setProperty("logSlowEvents", Boolean.toString(logSlowEvents));
        properties.setProperty("summaryIntervalTicks", Integer.toString(summaryIntervalTicks));
        properties.setProperty("worldSampleIntervalTicks", Integer.toString(worldSampleIntervalTicks));
        properties.setProperty("topEntryLimit", Integer.toString(topEntryLimit));
        properties.setProperty("slowWarnMs", Long.toString(slowWarnMs));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                properties.store(writer, "MasuCraftFixes Stage 0 telemetry runtime settings");
            }
        } catch (IOException exception) {
            MasuCraftFixes.LOGGER.warn("[Stage0] Could not save {}", CONFIG_PATH, exception);
        }
    }

    private static boolean boolValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int intValue(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
}
