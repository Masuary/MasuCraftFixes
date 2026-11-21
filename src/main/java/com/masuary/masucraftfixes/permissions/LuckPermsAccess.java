package com.masuary.masucraftfixes.permissions;

import com.masuary.masucraftfixes.MasuCraftFixes;
import com.mojang.authlib.GameProfile;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class LuckPermsAccess {

    public static final String COMMAND_BLOCK_EDIT_PERMISSION = "masucraftfixes.commandblock.edit";
    public static final String IGNORES_PLAYER_LIMIT_PERMISSION = "masucraftfixes.ignores_player_limit";

    private LuckPermsAccess() {
    }

    public static boolean canEditCommandBlocks(ServerPlayer player) {
        return permissionIfAvailable(player, COMMAND_BLOCK_EDIT_PERMISSION).orElse(false);
    }

    /** False when LuckPerms has not loaded this user (it loads users during login negotiation). */
    public static boolean loadedUserHasPermission(GameProfile profile, String permission) {
        User user = LuckPermsProvider.get().getUserManager().getUser(profile.getId());
        if (user == null) {
            MasuCraftFixes.LOGGER.warn("LuckPerms has not loaded {} ({}); {} counts as not granted",
                    profile.getName(), profile.getId(), permission);
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
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
