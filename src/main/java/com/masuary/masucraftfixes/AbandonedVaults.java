package com.masuary.masucraftfixes;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultUtils;
import iskallia.vault.core.vault.player.Listener;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.world.data.ServerVaults;
import iskallia.vault.world.data.VirtualWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vault worlds stay loaded until their vault ends, and a vault waits for its players to return.
 * Runs whose players stopped playing on this server, and worlds whose vault no longer exists
 * (for example after a Vault Hunters update that cannot read older vaults), are closed here.
 */
public final class AbandonedVaults {

    public static final Duration ABANDONED_AFTER = Duration.ofDays(7);
    private static final String VAULT_NAMESPACE = "the_vault";
    private static final Pattern VAULT_WORLD_PATH = Pattern.compile(
            "vault_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private AbandonedVaults() {
    }

    public static void cleanUp(MinecraftServer server) {
        closeAbandonedVaults(server);
        markWorldsWithoutVault();
        deleteUnregisteredWorldFolders(server);
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

    private static void closeAbandonedVaults(MinecraftServer server) {
        Instant cutoff = Instant.now().minus(ABANDONED_AFTER);
        for (Vault vault : new ArrayList<>(ServerVaults.getAll())) {
            if (vault.has(Vault.FINISHED) || !vault.has(Vault.LISTENERS) || !isAbandoned(server, vault, cutoff)) {
                continue;
            }
            Optional<VirtualWorld> world = ServerVaults.getWorld(vault);
            if (world.isEmpty()) {
                MasuCraftFixes.LOGGER.warn("Abandoned vault {} has no world to close; leaving it to Vault Hunters", vault.get(Vault.ID));
                continue;
            }
            VaultUtils.closeVault(world.get(), vault, "no player in it has played on this server for " + ABANDONED_AFTER.toDays() + " days");
        }
    }

    private static boolean isAbandoned(MinecraftServer server, Vault vault, Instant cutoff) {
        for (Listener listener : vault.get(Vault.LISTENERS).getAll()) {
            UUID playerId = listener.getId();
            if (server.getPlayerList().getPlayer(playerId) != null) {
                return false;
            }
            Path playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(playerId + ".dat");
            try {
                if (Files.isRegularFile(playerFile, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(playerFile, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff)) {
                    return false;
                }
            } catch (IOException exception) {
                MasuCraftFixes.LOGGER.warn("Cannot read the last save of {}; keeping vault {}", playerId, vault.get(Vault.ID), exception);
                return false;
            }
        }
        return true;
    }

    private static void markWorldsWithoutVault() {
        for (VirtualWorld world : new ArrayList<>(VirtualWorlds.getAll())) {
            Optional<UUID> vaultId = vaultId(world.dimension().location());
            if (vaultId.isEmpty() || world.isMarkedForDeletion()
                    || ServerVaults.get(vaultId.get()).filter(vault -> !vault.has(Vault.FINISHED)).isPresent()) {
                continue;
            }
            world.markForDeletion();
            MasuCraftFixes.LOGGER.info("Marked vault world {} for deletion: its vault no longer exists", world.dimension().location());
        }
    }

    private static void deleteUnregisteredWorldFolders(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(VAULT_NAMESPACE);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<String> registered = new HashSet<>();
        for (VirtualWorld world : VirtualWorlds.getAll()) {
            ResourceLocation id = world.dimension().location();
            if (id.getNamespace().equals(VAULT_NAMESPACE)) {
                registered.add(id.getPath());
            }
        }
        try (var entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!VAULT_WORLD_PATH.matcher(name).matches() || registered.contains(name)
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                deleteTree(entry);
                MasuCraftFixes.LOGGER.info("Deleted unregistered vault world folder {}", entry);
            }
        } catch (IOException exception) {
            MasuCraftFixes.LOGGER.error("Failed to delete unregistered vault world folders in {}", folder, exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Optional<UUID> vaultId(ResourceLocation dimension) {
        if (!dimension.getNamespace().equals(VAULT_NAMESPACE)) {
            return Optional.empty();
        }
        Matcher matcher = VAULT_WORLD_PATH.matcher(dimension.getPath());
        return matcher.matches() ? Optional.of(UUID.fromString(matcher.group(1))) : Optional.empty();
    }
}
