package com.masuary.masucraftfixes.permissions;

import com.masuary.masucraftfixes.MasuCraftFixes;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class LuckPermsAccess {

    public static final String COMMAND_BLOCK_EDIT_PERMISSION = "masucraftfixes.commandblock.edit";
    public static final String IGNORES_PLAYER_LIMIT_PERMISSION = "masucraftfixes.ignores_player_limit";

    private LuckPermsAccess() {
    }

    public static boolean doesIgnoreLimit(ServerPlayer player) {
        Optional<Boolean> ignoresLimit = permissionIfAvailable(player, IGNORES_PLAYER_LIMIT_PERMISSION);
        if (ignoresLimit.isEmpty()) {
            MasuCraftFixes.LOGGER.debug("LuckPerms data unavailable for {}, allowing login", player.getGameProfile().getName());
        }
        return ignoresLimit.orElse(true);
    }

    public static boolean canEditCommandBlocks(ServerPlayer player) {
        return permissionIfAvailable(player, COMMAND_BLOCK_EDIT_PERMISSION).orElse(false);
    }

    /**
     * Empty when LuckPerms is not enabled yet or no longer holds data for this player entity. Vanilla removes a
     * player 20 ticks after death, and Forge then invalidates the LuckPerms capability until the player respawns.
     */
    public static Optional<Boolean> permissionIfAvailable(ServerPlayer player, String permission) {
        if (player.isRemoved()) {
            return Optional.empty();
        }
        try {
            PlayerAdapter<ServerPlayer> adapter = LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class);
            CachedPermissionData permissionData = adapter.getPermissionData(player);
            return Optional.of(permissionData.checkPermission(permission).asBoolean());
        } catch (IllegalStateException exception) {
            MasuCraftFixes.LOGGER.debug("LuckPerms could not check {} for {}: {}",
                    permission, player.getGameProfile().getName(), exception.getMessage());
            return Optional.empty();
        }
    }
}
