package com.masuary.masucraftfixes;

import com.masuary.masucraftfixes.permissions.PlayerLimitBypass;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MasuCraftFixes.MOD_ID)
public class MasuCraftFixes {

    public static final String MOD_ID = "masucraftfixes";
    public static final Logger LOGGER = LogManager.getLogger();

    public MasuCraftFixes() {
        MinecraftForge.EVENT_BUS.register(new PlayerLimitBypass());
    }
}
