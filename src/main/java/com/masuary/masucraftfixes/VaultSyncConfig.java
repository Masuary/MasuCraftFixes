package com.masuary.masucraftfixes;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VaultSyncConfig {
    private static final Path CONFIG_PATH = Path.of("config", "masucraftfixes-vaultsync.properties");
    private static final Path TELEMETRY_LOG_PATH = Path.of("logs", "masucraftfixes-vaultsync.log");

    private static volatile boolean loaded;
    private static volatile boolean telemetryEnabled;
    private static volatile boolean logEachSync;
    private static volatile int summaryIntervalTicks;
    private static volatile int fullWarnBytes;
    private static volatile int hudWarnBytes;
    private static volatile long slowWarnMs;
    private static volatile int fullRefreshIntervalTicks;

    private VaultSyncConfig() {
    }

    public static void bootstrap() {
        ensureLoaded();
    }

    public static boolean isTelemetryEnabled() {
        ensureLoaded();
        return telemetryEnabled;
    }

    public static boolean logEachSync() {
        ensureLoaded();
        return logEachSync;
    }

    public static int summaryIntervalTicks() {
        ensureLoaded();
        return summaryIntervalTicks;
    }

    public static int fullWarnBytes() {
        ensureLoaded();
        return fullWarnBytes;
    }

    public static int hudWarnBytes() {
        ensureLoaded();
        return hudWarnBytes;
    }

    public static long slowWarnNanos() {
        ensureLoaded();
        return Math.max(0L, slowWarnMs) * 1_000_000L;
    }

    public static long slowWarnMs() {
        ensureLoaded();
        return slowWarnMs;
    }

    public static int fullRefreshIntervalTicks() {
        ensureLoaded();
        return fullRefreshIntervalTicks;
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static Path telemetryLogPath() {
        return TELEMETRY_LOG_PATH;
    }

    public static void setTelemetryEnabled(boolean enabled) {
        ensureLoaded();
        synchronized (VaultSyncConfig.class) {
            telemetryEnabled = enabled;
            saveLocked();
        }
    }

    public static void setLogEachSync(boolean enabled) {
        ensureLoaded();
        synchronized (VaultSyncConfig.class) {
            logEachSync = enabled;
            saveLocked();
        }
    }

    public static void setSummaryIntervalTicks(int ticks) {
        ensureLoaded();
        synchronized (VaultSyncConfig.class) {
            summaryIntervalTicks = Math.max(20, ticks);
            saveLocked();
        }
    }

    public static void setSlowWarnMs(long ms) {
        ensureLoaded();
        synchronized (VaultSyncConfig.class) {
            slowWarnMs = Math.max(0L, ms);
            saveLocked();
        }
    }

    public static void setFullRefreshIntervalTicks(int ticks) {
        ensureLoaded();
        synchronized (VaultSyncConfig.class) {
            fullRefreshIntervalTicks = Math.max(1, ticks);
            saveLocked();
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (VaultSyncConfig.class) {
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
                    MasuCraftFixes.LOGGER.warn("[VaultSync] Could not load {}, using defaults", CONFIG_PATH, exception);
                }
            } else {
                saveLocked();
            }

            loaded = true;
        }
    }

    private static void applyDefaults() {
        telemetryEnabled = boolProperty("masucraftfixes.vaultSync.telemetryEnabled", false);
        logEachSync = boolProperty("masucraftfixes.vaultSync.logEachSync", false);
        summaryIntervalTicks = Math.max(20, intProperty("masucraftfixes.vaultSync.summaryIntervalTicks", 600));
        fullWarnBytes = Math.max(0, intProperty("masucraftfixes.vaultSync.logFullOverBytes", 524288));
        hudWarnBytes = Math.max(0, intProperty("masucraftfixes.vaultSync.logHudDiffOverBytes", 131072));
        slowWarnMs = Math.max(0L, longProperty("masucraftfixes.vaultSync.logSlowOverMs", 10L));
        fullRefreshIntervalTicks = Math.max(1, intProperty("masucraftfixes.vaultSync.fullRefreshIntervalTicks", 20));
    }

    private static void apply(Properties properties) {
        telemetryEnabled = boolValue(properties, "telemetryEnabled", telemetryEnabled);
        logEachSync = boolValue(properties, "logEachSync", logEachSync);
        summaryIntervalTicks = Math.max(20, intValue(properties, "summaryIntervalTicks", summaryIntervalTicks));
        fullWarnBytes = Math.max(0, intValue(properties, "logFullOverBytes", fullWarnBytes));
        hudWarnBytes = Math.max(0, intValue(properties, "logHudDiffOverBytes", hudWarnBytes));
        slowWarnMs = Math.max(0L, longValue(properties, "logSlowOverMs", slowWarnMs));
        fullRefreshIntervalTicks = Math.max(1, intValue(properties, "fullRefreshIntervalTicks", fullRefreshIntervalTicks));
    }

    private static void saveLocked() {
        Properties properties = new Properties();
        properties.setProperty("telemetryEnabled", Boolean.toString(telemetryEnabled));
        properties.setProperty("logEachSync", Boolean.toString(logEachSync));
        properties.setProperty("summaryIntervalTicks", Integer.toString(summaryIntervalTicks));
        properties.setProperty("logFullOverBytes", Integer.toString(fullWarnBytes));
        properties.setProperty("logHudDiffOverBytes", Integer.toString(hudWarnBytes));
        properties.setProperty("logSlowOverMs", Long.toString(slowWarnMs));
        properties.setProperty("fullRefreshIntervalTicks", Integer.toString(fullRefreshIntervalTicks));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                properties.store(writer, "MasuCraftFixes VaultSync runtime settings");
            }
        } catch (IOException exception) {
            MasuCraftFixes.LOGGER.warn("[VaultSync] Could not save {}", CONFIG_PATH, exception);
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
