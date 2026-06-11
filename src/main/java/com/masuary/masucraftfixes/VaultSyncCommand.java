package com.masuary.masucraftfixes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class VaultSyncCommand {
    private VaultSyncCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vaultsync")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource())))
                        .then(Commands.literal("debug")
                                .executes(context -> showTelemetryStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setTelemetry(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("telemetry")
                                .executes(context -> showTelemetryStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setTelemetry(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("log-each-sync")
                                .executes(context -> showLogEachSyncStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setLogEachSync(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("summary-interval")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20))
                                        .executes(context -> setSummaryInterval(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("slow-threshold")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0))
                                        .executes(context -> setSlowThreshold(context.getSource(), IntegerArgumentType.getInteger(context, "ms")))))
                        .then(Commands.literal("full-refresh-interval")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(context -> setFullRefreshInterval(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
        );
    }

    private static int showStatus(CommandSourceStack source) {
        send(source, "telemetry=" + onOff(VaultSyncConfig.isTelemetryEnabled())
                + " logEachSync=" + onOff(VaultSyncConfig.logEachSync())
                + " logFile=" + VaultSyncConfig.telemetryLogPath());
        send(source, "summaryIntervalTicks=" + VaultSyncConfig.summaryIntervalTicks()
                + " fullRefreshIntervalTicks=" + VaultSyncConfig.fullRefreshIntervalTicks()
                + " slowThresholdMs=" + VaultSyncConfig.slowWarnMs());
        send(source, "counters " + VaultSyncTelemetry.currentSummary());
        return 1;
    }

    private static int showTelemetryStatus(CommandSourceStack source) {
        send(source, "telemetry/debug is currently " + onOff(VaultSyncConfig.isTelemetryEnabled())
                + "; file=" + VaultSyncConfig.telemetryLogPath());
        return 1;
    }

    private static int showLogEachSyncStatus(CommandSourceStack source) {
        send(source, "logEachSync is currently " + onOff(VaultSyncConfig.logEachSync()));
        return 1;
    }

    private static int setTelemetry(CommandSourceStack source, boolean enabled) {
        VaultSyncConfig.setTelemetryEnabled(enabled);
        send(source, "telemetry/debug " + (enabled ? "enabled" : "disabled")
                + " and saved to " + VaultSyncConfig.configPath());
        MasuCraftFixes.LOGGER.info("[VaultSync] telemetry/debug {} by {}", enabled ? "enabled" : "disabled", source.getTextName());
        return 1;
    }

    private static int setLogEachSync(CommandSourceStack source, boolean enabled) {
        VaultSyncConfig.setLogEachSync(enabled);
        send(source, "logEachSync " + (enabled ? "enabled" : "disabled")
                + " and saved to " + VaultSyncConfig.configPath());
        return 1;
    }

    private static int setSummaryInterval(CommandSourceStack source, int ticks) {
        VaultSyncConfig.setSummaryIntervalTicks(ticks);
        send(source, "summaryIntervalTicks set to " + VaultSyncConfig.summaryIntervalTicks()
                + " and saved to " + VaultSyncConfig.configPath());
        return 1;
    }

    private static int setSlowThreshold(CommandSourceStack source, int ms) {
        VaultSyncConfig.setSlowWarnMs(ms);
        send(source, "slowThresholdMs set to " + VaultSyncConfig.slowWarnMs()
                + " and saved to " + VaultSyncConfig.configPath());
        return 1;
    }

    private static int setFullRefreshInterval(CommandSourceStack source, int ticks) {
        VaultSyncConfig.setFullRefreshIntervalTicks(ticks);
        send(source, "fullRefreshIntervalTicks set to " + VaultSyncConfig.fullRefreshIntervalTicks()
                + " and saved to " + VaultSyncConfig.configPath());
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(new TextComponent("[VaultSync] " + message), false);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }
}
