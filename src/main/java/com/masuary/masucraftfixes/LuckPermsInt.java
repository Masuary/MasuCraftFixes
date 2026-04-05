package com.masuary.masucraftfixes;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class LuckPermsInt {

    public static boolean doesIgnoreLimit(ServerPlayer player) {
        try {
            LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            MasuCraftFixes.LOGGER.debug("LuckPerms not loaded yet, allowing login");
            return true;
        }

        PlayerAdapter<ServerPlayer> adapter = LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class);
        CachedPermissionData permissionData = adapter.getPermissionData(player);

        return permissionData.checkPermission("masucraftfixes.ignores_player_limit").asBoolean();
    }

    public static boolean canEditCommandBlocks(ServerPlayer player) {
        try {
            LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            return true;
        }

        PlayerAdapter<ServerPlayer> adapter = LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class);
        CachedPermissionData permissionData = adapter.getPermissionData(player);

        return permissionData.checkPermission("masucraftfixes.commandblock.edit").asBoolean();
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        try {
            LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            return false;
        }

        PlayerAdapter<ServerPlayer> adapter = LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class);
        CachedPermissionData permissionData = adapter.getPermissionData(player);

        return permissionData.checkPermission(permission).asBoolean();
    }

    public static boolean hasPermission(UUID uuid, String permission) {
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(uuid);
            if (user == null) return false;
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public static void subscribeToPermissionChanges(java.util.function.Consumer<java.util.UUID> handler) {
        try {
            net.luckperms.api.LuckPerms luckPerms = LuckPermsProvider.get();
            luckPerms.getEventBus().subscribe(
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> handler.accept(event.getUser().getUniqueId())
            );
            MasuCraftFixes.LOGGER.info("Subscribed to LuckPerms permission change events for patron perks");
        } catch (Exception e) {
            MasuCraftFixes.LOGGER.warn("Failed to subscribe to LuckPerms events: {}", e.getMessage());
        }
    }
}
