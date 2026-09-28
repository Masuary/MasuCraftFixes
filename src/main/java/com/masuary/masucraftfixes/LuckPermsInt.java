package com.masuary.masucraftfixes;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public class LuckPermsInt {

    public static boolean doesIgnoreLimit(ServerPlayer player) {
        Optional<Boolean> ignoresLimit = permissionIfAvailable(player, "masucraftfixes.ignores_player_limit");
        if (ignoresLimit.isEmpty()) {
            MasuCraftFixes.LOGGER.debug("LuckPerms data unavailable for {}, allowing login", player.getGameProfile().getName());
        }
        return ignoresLimit.orElse(true);
    }

    public static boolean canEditCommandBlocks(ServerPlayer player) {
        return permissionIfAvailable(player, "masucraftfixes.commandblock.edit").orElse(false);
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
