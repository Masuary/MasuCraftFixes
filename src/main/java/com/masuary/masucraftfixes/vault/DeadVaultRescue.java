package com.masuary.masucraftfixes.vault;

import com.masuary.masucraftfixes.MasuCraftFixes;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A player saved inside a vault world that is gone, marked for deletion or has no running vault would be placed by
 * vanilla in the overworld at the vault coordinates (on Skyvault usually the void). Vault Hunters only rescues
 * players from a loaded vault world without a vault, so the missing world is handled here, the same way VH does it:
 * respawn point first, world spawn otherwise, and a message once the player is in.
 */
public class DeadVaultRescue {

    private static final Set<UUID> PLAYERS_AWAITING_MESSAGE = new HashSet<>();

    /** Runs inside {@code PlayerList.placeNewPlayer}, before the player is added to a level. */
    public static ResourceKey<Level> rescueFromDeadVault(MinecraftServer server, ServerPlayer player, ResourceKey<Level> dimension) {
        Optional<UUID> vaultId = VaultDimensions.vaultId(dimension.location());
        if (vaultId.isEmpty() || isLiveVaultWorld(server, dimension, vaultId.get())) {
            return dimension;
        }
        ResourceKey<Level> rescueDimension = moveToRespawnPoint(server, player).orElseGet(() -> moveToWorldSpawn(server, player));
        player.fallDistance = 0.0F;
        PLAYERS_AWAITING_MESSAGE.add(player.getUUID());
        MasuCraftFixes.LOGGER.info("Moved {} out of dead vault world {} to {} at {}",
                player.getGameProfile().getName(), dimension.location(), rescueDimension.location(), player.blockPosition());
        return rescueDimension;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && PLAYERS_AWAITING_MESSAGE.remove(player.getUUID())) {
            player.sendMessage(new TextComponent("Your vault could not be loaded, so you were sent back to your spawn point.")
                    .withStyle(ChatFormatting.GOLD), player.getUUID());
        }
    }

    private static Optional<ResourceKey<Level>> moveToRespawnPoint(MinecraftServer server, ServerPlayer player) {
        BlockPos respawnPosition = player.getRespawnPosition();
        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        if (respawnPosition == null || respawnLevel == null || VaultDimensions.isVaultWorld(respawnLevel)) {
            return Optional.empty();
        }
        Optional<Vec3> standUpPosition = Player.findRespawnPositionAndUseSpawnBlock(
                respawnLevel, respawnPosition, player.getRespawnAngle(), player.isRespawnForced(), true);
        if (standUpPosition.isEmpty()) {
            return Optional.empty();
        }
        Vec3 position = standUpPosition.get();
        player.moveTo(position.x, position.y, position.z, player.getRespawnAngle(), 0.0F);
        return Optional.of(respawnLevel.dimension());
    }

    private static ResourceKey<Level> moveToWorldSpawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, overworld.getSharedSpawnAngle(), 0.0F);
        return Level.OVERWORLD;
    }

    private static boolean isLiveVaultWorld(MinecraftServer server, ResourceKey<Level> dimension, UUID vaultId) {
        ServerLevel level = server.getLevel(dimension);
        if (!(level instanceof VirtualWorld world) || world.isMarkedForDeletion()) {
            return false;
        }
        return ServerVaults.get(vaultId).filter(vault -> !vault.has(Vault.FINISHED)).isPresent();
    }
}
