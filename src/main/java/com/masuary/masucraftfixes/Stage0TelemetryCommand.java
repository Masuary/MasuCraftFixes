package com.masuary.masucraftfixes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class Stage0TelemetryCommand {
    private Stage0TelemetryCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("masucraftstage0")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource())))
                        .then(Commands.literal("telemetry")
                                .executes(context -> showTelemetryStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setTelemetry(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("debug")
                                .executes(context -> showTelemetryStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setTelemetry(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("log-slow-events")
                                .executes(context -> showLogSlowStatus(context.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setLogSlowEvents(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("summary-interval")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20))
                                        .executes(context -> setSummaryInterval(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("world-sample-interval")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20))
                                        .executes(context -> setWorldSampleInterval(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("slow-threshold")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0))
                                        .executes(context -> setSlowThreshold(context.getSource(), IntegerArgumentType.getInteger(context, "ms")))))
                        .then(Commands.literal("top-entry-limit")
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                        .executes(context -> setTopEntryLimit(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))))
                        .then(Commands.literal("reset")
                                .executes(context -> reset(context.getSource())))
        );
    }

    private static int showStatus(CommandSourceStack source) {
        send(source, "telemetry=" + onOff(Stage0TelemetryConfig.isTelemetryEnabled())
                + " logSlowEvents=" + onOff(Stage0TelemetryConfig.logSlowEvents())
                + " logFile=" + Stage0TelemetryConfig.telemetryLogPath());
        send(source, "summaryIntervalTicks=" + Stage0TelemetryConfig.summaryIntervalTicks()
                + " worldSampleIntervalTicks=" + Stage0TelemetryConfig.worldSampleIntervalTicks()
                + " slowThresholdMs=" + Stage0TelemetryConfig.slowWarnMs()
                + " topEntryLimit=" + Stage0TelemetryConfig.topEntryLimit());
        send(source, "counters " + Stage0Telemetry.currentSummary());
        return 1;
    }

    private static int showTelemetryStatus(CommandSourceStack source) {
        send(source, "telemetry/debug is currently " + onOff(Stage0TelemetryConfig.isTelemetryEnabled())
                + "; file=" + Stage0TelemetryConfig.telemetryLogPath());
        return 1;
    }

    private static int showLogSlowStatus(CommandSourceStack source) {
        send(source, "logSlowEvents is currently " + onOff(Stage0TelemetryConfig.logSlowEvents()));
        return 1;
    }

    private static int setTelemetry(CommandSourceStack source, boolean enabled) {
        Stage0TelemetryConfig.setTelemetryEnabled(enabled);
        send(source, "telemetry/debug " + (enabled ? "enabled" : "disabled")
                + " and saved to " + Stage0TelemetryConfig.configPath());
        MasuCraftFixes.LOGGER.info("[Stage0] telemetry/debug {} by {}", enabled ? "enabled" : "disabled", source.getTextName());
        return 1;
    }

    private static int setLogSlowEvents(CommandSourceStack source, boolean enabled) {
        Stage0TelemetryConfig.setLogSlowEvents(enabled);
        send(source, "logSlowEvents " + (enabled ? "enabled" : "disabled")
                + " and saved to " + Stage0TelemetryConfig.configPath());
        return 1;
    }

    private static int setSummaryInterval(CommandSourceStack source, int ticks) {
        Stage0TelemetryConfig.setSummaryIntervalTicks(ticks);
        send(source, "summaryIntervalTicks set to " + Stage0TelemetryConfig.summaryIntervalTicks()
                + " and saved to " + Stage0TelemetryConfig.configPath());
        return 1;
    }

    private static int setWorldSampleInterval(CommandSourceStack source, int ticks) {
        Stage0TelemetryConfig.setWorldSampleIntervalTicks(ticks);
        send(source, "worldSampleIntervalTicks set to " + Stage0TelemetryConfig.worldSampleIntervalTicks()
                + " and saved to " + Stage0TelemetryConfig.configPath());
        return 1;
    }

    private static int setSlowThreshold(CommandSourceStack source, int ms) {
        Stage0TelemetryConfig.setSlowWarnMs(ms);
        send(source, "slowThresholdMs set to " + Stage0TelemetryConfig.slowWarnMs()
                + " and saved to " + Stage0TelemetryConfig.configPath());
        return 1;
    }

    private static int setTopEntryLimit(CommandSourceStack source, int limit) {
        Stage0TelemetryConfig.setTopEntryLimit(limit);
        send(source, "topEntryLimit set to " + Stage0TelemetryConfig.topEntryLimit()
                + " and saved to " + Stage0TelemetryConfig.configPath());
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        Stage0Telemetry.reset();
        send(source, "counters reset");
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(new TextComponent("[Stage0] " + message), false);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }
}
