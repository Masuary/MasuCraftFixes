package com.masuary.masucraftfixes.command;

import com.masuary.masucraftfixes.MasuCraftFixes;
import com.masuary.masucraftfixes.refinedstorage.RefinedStorageStatus;
import com.masuary.masucraftfixes.vault.CrystalCommandSuggestions;
import com.masuary.masucraftfixes.vault.DeadVaultRescue;
import com.mojang.brigadier.context.CommandContext;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** {@code /masucraftfixes status}: what the mod is doing on this backend, for staff. */
public class StatusCommand {

    private static final int STAFF_PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MasuCraftFixes.MOD_ID)
                .requires(source -> source.hasPermission(STAFF_PERMISSION_LEVEL))
                .then(Commands.literal("status").executes(StatusCommand::showStatus)));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> lines = new ArrayList<>();
        lines.add("MasuCraftFixes " + modVersion());
        lines.add("LuckPerms: " + luckPermsState() + " (player limit bypass and command block edit nodes)");
        lines.add("Crystal addRoom pool suggestions: " + (CrystalCommandSuggestions.isAvailable() ? "on" : "OFF, see the startup log"));
        lines.add("Players rescued from dead vault worlds since start: " + DeadVaultRescue.rescuedSinceStart());
        lines.addAll(refinedStorageLines(source));
        source.sendSuccess(new TextComponent(lines.get(0)).withStyle(ChatFormatting.GOLD), false);
        for (String line : lines.subList(1, lines.size())) {
            source.sendSuccess(new TextComponent(line), false);
        }
        return lines.size();
    }

    private static String modVersion() {
        return ModList.get().getModContainerById(MasuCraftFixes.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElseThrow(() -> new IllegalStateException("MasuCraftFixes is not in the mod list"));
    }

    private static String luckPermsState() {
        try {
            LuckPermsProvider.get();
            return "available";
        } catch (IllegalStateException notLoadedYet) {
            return "NOT available: " + notLoadedYet.getMessage();
        }
    }

    private static List<String> refinedStorageLines(CommandSourceStack source) {
        if (!ModList.get().isLoaded("refinedstorage")) {
            return List.of("Refined Storage: not installed, its fixes are off");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Refined Storage chunk fix: on");
        lines.addAll(RefinedStorageStatus.describeLevels(source.getServer()));
        return lines;
    }
}
