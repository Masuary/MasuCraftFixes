package com.masuary.masucraftfixes;

import com.masuary.masucraftfixes.command.StatusCommand;
import com.masuary.masucraftfixes.flight.VaultFlightRule;
import com.masuary.masucraftfixes.recipes.KnownBrokenPackRecipes;
import com.masuary.masucraftfixes.recipes.KnownBrokenRecipeLogFilter;
import com.masuary.masucraftfixes.refinedstorage.RefinedStorageReconnects;
import com.masuary.masucraftfixes.vault.DeadVaultRescue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MasuCraftFixes.MOD_ID)
public class MasuCraftFixes {

    public static final String MOD_ID = "masucraftfixes";
    public static final Logger LOGGER = LogManager.getLogger();

    public MasuCraftFixes() {
        KnownBrokenRecipeLogFilter.install(KnownBrokenPackRecipes.bundled());
        MinecraftForge.EVENT_BUS.register(new VaultFlightRule());
        MinecraftForge.EVENT_BUS.register(new DeadVaultRescue());
        MinecraftForge.EVENT_BUS.register(new StatusCommand());
        if (ModList.get().isLoaded("refinedstorage")) {
            MinecraftForge.EVENT_BUS.register(new RefinedStorageReconnects.ServerStopListener());
        }
    }
}
