package com.masuary.masucraftfixes;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;

@Mod("masucraftfixes")
public class MasuCraftFixes {

    public static final Logger LOGGER = LogManager.getLogger();

    public MasuCraftFixes() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.register(new VesselAntiAfk());
        MinecraftForge.EVENT_BUS.register(VesselAntiAfkCommand.class);
        LOGGER.info("MasuCraftFixes initialized");
    }

    private void setup(final FMLCommonSetupEvent event) {
        MixinBootstrap.init();
    }
}
