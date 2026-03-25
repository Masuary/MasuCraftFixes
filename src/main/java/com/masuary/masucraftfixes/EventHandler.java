package com.masuary.masucraftfixes;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class EventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer) || !serverPlayer.isAlive()) {
            return;
        }

        boolean luckPermsLoaded = ModList.get().isLoaded("luckperms");

        if (luckPermsLoaded) {
            if (LuckPermsInt.doesIgnoreLimit(serverPlayer)) {
                serverPlayer.addTag("ignores_player_limit");
                return;
            } else {
                serverPlayer.removeTag("ignores_player_limit");
            }
        }

        PlayerList playerList = serverPlayer.server.getPlayerList();
        int playerCount;

        if (luckPermsLoaded) {
            playerCount = 0;
            for (ServerPlayer player : playerList.getPlayers()) {
                if (!player.getTags().contains("ignores_player_limit")) {
                    playerCount++;
                }
            }
        } else {
            playerCount = playerList.getPlayers().size();
        }

        if (playerCount > playerList.getMaxPlayers()) {
            serverPlayer.connection.disconnect(
                    new TranslatableComponent("multiplayer.disconnect.server_full")
            );
        }
    }
}
