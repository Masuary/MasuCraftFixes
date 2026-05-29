package com.masuary.masucraftfixes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class VesselAntiAfkCommand {

    private VesselAntiAfkCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vesselantiafk")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .executes(ctx -> showDebugStatus(ctx.getSource()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setDebug(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
        );
    }

    private static int showDebugStatus(CommandSourceStack source) {
        boolean enabled = VesselAntiAfk.isDebug();
        source.sendSuccess(new TextComponent("[VesselAntiAfk] debug is currently " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setDebug(CommandSourceStack source, boolean enabled) {
        VesselAntiAfk.setDebug(enabled);
        source.sendSuccess(new TextComponent("[VesselAntiAfk] debug " + (enabled ? "enabled" : "disabled")), true);
        MasuCraftFixes.LOGGER.info("[VesselAntiAfk] debug {} by {}", enabled ? "enabled" : "disabled", source.getTextName());
        return 1;
    }
}
