package com.masuary.masucraftfixes;

import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

public final class FtbEssentialsFlightCompatibility {
    private static final String VAULT_DIMENSION_ID_FRAGMENT = "the_vault:vault";

    private FtbEssentialsFlightCompatibility() {
    }

    public static boolean shouldPreserveFlightFromAngelExpertise(ServerPlayer player) {
        FTBEPlayerData playerData = FTBEPlayerData.get(player);
        if (playerData == null || !playerData.fly) {
            return false;
        }

        return !isVaultDimension(player) || player.hasPermissions(4);
    }

    public static boolean isVaultDimension(ServerPlayer player) {
        String dimensionId = player.level.dimension().location().toString();
        return dimensionId.toLowerCase(Locale.ROOT).contains(VAULT_DIMENSION_ID_FRAGMENT);
    }
}
