package com.masuary.masucraftfixes.flight;

import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

public final class FtbFlight {

    private FtbFlight() {
    }

    public static boolean isActive(ServerPlayer player) {
        FTBEPlayerData data = FTBEPlayerData.get(player);
        return data != null && data.fly;
    }

    public static void disable(ServerPlayer player) {
        FTBEPlayerData data = FTBEPlayerData.get(player);
        if (data == null || !data.fly) {
            return;
        }
        data.fly = false;
        data.save();
        Abilities abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.displayClientMessage(new TextComponent("Flight disabled"), true);
        player.onUpdateAbilities();
    }
}
