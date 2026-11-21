package com.masuary.masucraftfixes;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A player saved inside a vault world that is gone, marked for deletion or has no running vault would be placed by
 * vanilla in the overworld at the vault coordinates. Vault Hunters only rescues players from a loaded vault world
 * without a vault, so a missing world is handled here.
 */
public final class DeadVaultRescue {

    private static final String VAULT_NAMESPACE = "the_vault";
    private static final Pattern VAULT_WORLD_PATH = Pattern.compile(
            "vault_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private DeadVaultRescue() {
    }

    /** Returns the overworld instead of a dead vault world and moves the player to world spawn. */
    public static ResourceKey<Level> rescueFromDeadVault(MinecraftServer server, ServerPlayer player, ResourceKey<Level> dimension) {
        Optional<UUID> vaultId = vaultId(dimension.location());
        if (vaultId.isEmpty() || isLiveVaultWorld(server, dimension, vaultId.get())) {
            return dimension;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, overworld.getSharedSpawnAngle(), 0.0F);
        player.fallDistance = 0.0F;
        MasuCraftFixes.LOGGER.info("Moved {} out of dead vault world {} to the overworld spawn {}",
                player.getGameProfile().getName(), dimension.location(), spawn);
        return Level.OVERWORLD;
    }

    private static boolean isLiveVaultWorld(MinecraftServer server, ResourceKey<Level> dimension, UUID vaultId) {
        ServerLevel level = server.getLevel(dimension);
        if (!(level instanceof VirtualWorld world) || world.isMarkedForDeletion()) {
            return false;
        }
        return ServerVaults.get(vaultId).filter(vault -> !vault.has(Vault.FINISHED)).isPresent();
    }

    private static Optional<UUID> vaultId(ResourceLocation dimension) {
        if (!dimension.getNamespace().equals(VAULT_NAMESPACE)) {
            return Optional.empty();
        }
        Matcher matcher = VAULT_WORLD_PATH.matcher(dimension.getPath());
        return matcher.matches() ? Optional.of(UUID.fromString(matcher.group(1))) : Optional.empty();
    }
}
