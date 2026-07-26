package com.masuary.masucraftfixes;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

public class EventHandler {

    private static boolean luckPermsEventSubscribed;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        VaultV166DiskMigration.migrateIfRequired(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer) || !serverPlayer.isAlive()) {
            return;
        }

        boolean luckPermsLoaded = ModList.get().isLoaded("luckperms");

        if (luckPermsLoaded) {
            if (LuckPermsInt.doesIgnoreLimit(serverPlayer)) {
                serverPlayer.addTag("ignores_player_limit");
            } else {
                serverPlayer.removeTag("ignores_player_limit");

                PlayerList playerList = serverPlayer.server.getPlayerList();
                int playerCount = 0;
                for (ServerPlayer player : playerList.getPlayers()) {
                    if (!player.getTags().contains("ignores_player_limit")) {
                        playerCount++;
                    }
                }

                if (playerCount > playerList.getMaxPlayers()) {
                    serverPlayer.connection.disconnect(
                            new TranslatableComponent("multiplayer.disconnect.server_full")
                    );
                    return;
                }
            }
        }

        if (luckPermsLoaded && ModList.get().isLoaded("the_vault")) {
            PatreonPerksHandler.scheduleUpdate(serverPlayer.getUUID());

            if (!luckPermsEventSubscribed) {
                luckPermsEventSubscribed = true;
                LuckPermsInt.subscribeToPermissionChanges(PatreonPerksHandler::onPermissionChange);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer && ModList.get().isLoaded("the_vault")) {
            PatreonPerksHandler.onPlayerLeave(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ModList.get().isLoaded("the_vault")) {
            PatreonPerksHandler.tick();
        }
    }

    @SubscribeEvent
    public void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(
                new PermissionNode<>("masucraftfixes", "patron.dweller",
                        PermissionTypes.BOOLEAN, (player, uuid, ctx) -> false),
                new PermissionNode<>("masucraftfixes", "patron.cheeser",
                        PermissionTypes.BOOLEAN, (player, uuid, ctx) -> false),
                new PermissionNode<>("masucraftfixes", "patron.goblin",
                        PermissionTypes.BOOLEAN, (player, uuid, ctx) -> false),
                new PermissionNode<>("masucraftfixes", "patron.champion",
                        PermissionTypes.BOOLEAN, (player, uuid, ctx) -> false),
                new PermissionNode<>("masucraftfixes", "patron.legend",
                        PermissionTypes.BOOLEAN, (player, uuid, ctx) -> false)
        );
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        if (ModList.get().isLoaded("the_vault")) {
            PatreonPerksHandler.registerCommand(event.getDispatcher());
        }
    }
}
