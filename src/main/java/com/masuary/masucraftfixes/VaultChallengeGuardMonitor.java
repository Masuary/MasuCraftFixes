package com.masuary.masucraftfixes;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

public final class VaultChallengeGuardMonitor {
    private static final Path LOG_PATH = Path.of("logs", "masucraftfixes-vault-challenge-guard.log");
    private static VaultChallengeGuardDiagnostics diagnostics;
    private static VaultChallengeGuardLog diagnosticLog;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        closeLog();
        diagnosticLog = new VaultChallengeGuardLog(LOG_PATH, "5 MB", 3,
                (message, exception) -> MasuCraftFixes.LOGGER.error(message, exception));
        diagnostics = new VaultChallengeGuardDiagnostics(diagnosticLog, System::nanoTime,
                installedVersion("masucraftfixes"), installedVersion("the_vault"));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && diagnostics != null) {
            diagnostics.tick();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        closeLog();
    }

    public static void recordCallback(boolean allowed) {
        if (diagnostics != null) {
            diagnostics.recordCallback(allowed);
        }
    }

    public static void recordBlockedChallenge(String details) {
        if (diagnostics != null) {
            diagnostics.recordBlockedChallenge(details);
        }
    }

    public static void recordDetachedChallenge(String details) {
        if (diagnostics != null) {
            diagnostics.recordDetachedChallenge(details);
        }
    }

    private static String installedVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not-installed");
    }

    private static void closeLog() {
        if (diagnostics != null) {
            diagnostics.stop();
            diagnostics = null;
        }
        if (diagnosticLog != null) {
            diagnosticLog.close();
            diagnosticLog = null;
        }
    }
}
