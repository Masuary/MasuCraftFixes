package com.masuary.masucraftfixes;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

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
}
