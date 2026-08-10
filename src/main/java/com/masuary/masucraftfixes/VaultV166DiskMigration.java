package com.masuary.masucraftfixes;

import iskallia.vault.core.Version;
import iskallia.vault.core.data.key.registry.KeyIndexResolver;
import iskallia.vault.core.data.sync.context.RegistryIndexSyncContext;
import iskallia.vault.core.net.ArrayBitBuffer;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.stat.VaultSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class VaultV166DiskMigration {

    private static final String ACTIVE_VAULT_DATA_FILE = "the_vault_Vaults.dat";
    private static final String SNAPSHOT_MANIFEST_FILE = "the_vault_VaultSnapshots.dat";
    private static final String SNAPSHOT_DIRECTORY = "vault_snapshots";
    private static final String BACKUP_DIRECTORY = "masucraftfixes-v166-backup";
    private static final String COMPLETION_MARKER = "masucraftfixes-v166-to-v167.properties";
    private static final String TEMP_SUFFIX = ".masucraftfixes-v167.tmp";
    private static final long FREE_SPACE_MARGIN_BYTES = 64L * 1024L * 1024L;
    private static final int PROGRESS_INTERVAL = 500;

    private VaultV166DiskMigration() {
    }

    public static void migrateIfRequired(MinecraftServer server) {
        if (!server.isDedicatedServer() || !VaultV166Compatibility.isInstalled()) {
            return;
        }

        Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("data").toAbsolutePath().normalize();
        try {
            migrateWorldData(dataDirectory);
            VaultV166Compatibility.restoreNativeSchemaAfterDiskMigration();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Vault v1_66 to v1_67 disk migration failed for " + dataDirectory
                            + ". The server was stopped before Vault world data loaded.",
                    exception
            );
        }
    }

    private static void migrateWorldData(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);

        Path activeVaultData = dataDirectory.resolve(ACTIVE_VAULT_DATA_FILE);
        Path snapshotManifest = dataDirectory.resolve(SNAPSHOT_MANIFEST_FILE);
        Path snapshotDirectory = dataDirectory.resolve(SNAPSHOT_DIRECTORY);
        Path backupDirectory = dataDirectory.resolve(BACKUP_DIRECTORY);
        Path completionMarker = dataDirectory.resolve(COMPLETION_MARKER);

        MigrationInventory inventory = inspectMigrationInventory(activeVaultData, snapshotDirectory);
        Set<UUID> quarantinedSnapshotIds = readQuarantinedSnapshotIds(snapshotManifest);
        Set<UUID> previouslyMigratedSnapshotIds = findVerifiedMigratedSnapshotIds(
                snapshotDirectory,
                backupDirectory
        );
        boolean quarantineCleanupRequired = hasQuarantinedSnapshotIds(
                snapshotManifest,
                previouslyMigratedSnapshotIds
        );
        boolean completionMarkerExists = Files.exists(completionMarker);
        List<Path> quarantinedLegacySnapshots = findQuarantinedLegacySnapshots(
                inventory.legacySnapshotFiles(),
                quarantinedSnapshotIds
        );
        List<Path> legacySnapshotsToMigrate = completionMarkerExists
                ? List.of()
                : quarantinedLegacySnapshots;
        boolean migrationRequired = inventory.legacyActiveVaultRecordCount() > 0
                || !legacySnapshotsToMigrate.isEmpty();
        if (!migrationRequired && !quarantineCleanupRequired) {
            if (completionMarkerExists) {
                MasuCraftFixes.LOGGER.info(
                        "Verified completed Vault v1_67 disk migration: {} active record(s), {} snapshot file(s), "
                                + "{} native-compatible v1_66 snapshot file(s), "
                                + "{} retained quarantined v1_66 snapshot file(s)",
                        inventory.activeVaultRecordCount(),
                        inventory.snapshotFileCount(),
                        inventory.legacySnapshotFiles().size() - quarantinedLegacySnapshots.size(),
                        quarantinedLegacySnapshots.size()
                );
            } else {
                writeCompletionMarker(
                        completionMarker,
                        backupDirectory,
                        inventory,
                        0,
                        0,
                        0,
                        0,
                        inventory.legacySnapshotFiles().size(),
                        0
                );
                MasuCraftFixes.LOGGER.info(
                        "Vault has no quarantined v1_66 snapshots requiring the legacy schema; "
                                + "wrote migration marker and left {} native-compatible v1_66 snapshot file(s) unchanged",
                        inventory.legacySnapshotFiles().size()
                );
            }
            return;
        }

        if (completionMarkerExists) {
            MasuCraftFixes.LOGGER.warn(
                    "Vault migration marker exists but {} legacy active record(s) or quarantined migrated "
                            + "snapshots remain; resuming migration",
                    inventory.legacyActiveVaultRecordCount(),
                    previouslyMigratedSnapshotIds.size()
            );
        }

        ensureBackupCapacity(
                dataDirectory,
                backupDirectory,
                snapshotManifest,
                inventory,
                legacySnapshotsToMigrate,
                quarantineCleanupRequired || !legacySnapshotsToMigrate.isEmpty()
        );
        Files.createDirectories(backupDirectory);
        writeRestoreInstructions(backupDirectory);

        MasuCraftFixes.LOGGER.warn(
                "Starting one-time Vault disk migration: {} active v1_66 record(s), "
                        + "{} quarantined v1_66 snapshot file(s); leaving {} native-compatible v1_66 file(s) unchanged",
                inventory.legacyActiveVaultRecordCount(),
                legacySnapshotsToMigrate.size(),
                inventory.legacySnapshotFiles().size() - legacySnapshotsToMigrate.size()
        );

        int migratedActiveRecords = migrateActiveVaultData(activeVaultData, dataDirectory, backupDirectory);
        SnapshotMigrationResult snapshotMigrationResult = migrateSnapshotFiles(
                legacySnapshotsToMigrate,
                dataDirectory,
                backupDirectory
        );
        Set<UUID> verifiedMigratedSnapshotIds = findVerifiedMigratedSnapshotIds(
                snapshotDirectory,
                backupDirectory
        );
        SnapshotQuarantineUpdate quarantineUpdate = updateSnapshotQuarantine(
                snapshotManifest,
                dataDirectory,
                backupDirectory,
                verifiedMigratedSnapshotIds,
                snapshotMigrationResult.decodeFailures()
        );

        MigrationInventory verifiedInventory = inspectMigrationInventory(activeVaultData, snapshotDirectory);
        if (verifiedInventory.legacyActiveVaultRecordCount() > 0) {
            throw new IOException(
                    "Post-migration verification found " + verifiedInventory.legacyActiveVaultRecordCount()
                            + " active v1_66 record(s)"
            );
        }
        Set<UUID> remainingQuarantinedSnapshotIds = readQuarantinedSnapshotIds(snapshotManifest);
        Set<UUID> migratedIdsStillQuarantined = new HashSet<>(remainingQuarantinedSnapshotIds);
        migratedIdsStillQuarantined.retainAll(verifiedMigratedSnapshotIds);
        if (!migratedIdsStillQuarantined.isEmpty()) {
            throw new IOException(
                    "Post-migration verification found " + migratedIdsStillQuarantined.size()
                            + " successfully migrated snapshot ID(s) still quarantined in " + snapshotManifest
            );
        }
        List<Path> unexpectedLegacySnapshots = findUnquarantinedLegacySnapshots(
                quarantinedLegacySnapshots,
                remainingQuarantinedSnapshotIds
        );
        if (!unexpectedLegacySnapshots.isEmpty()) {
            throw new IOException(
                    "Post-migration verification found " + unexpectedLegacySnapshots.size()
                            + " unquarantined v1_66 snapshot file(s); first: " + unexpectedLegacySnapshots.get(0)
            );
        }
        int retainedQuarantinedSnapshots = countLegacySnapshotFiles(quarantinedLegacySnapshots);
        int nativeCompatibleLegacySnapshots = verifiedInventory.legacySnapshotFiles().size()
                - retainedQuarantinedSnapshots;

        writeCompletionMarker(
                completionMarker,
                backupDirectory,
                verifiedInventory,
                migratedActiveRecords,
                snapshotMigrationResult.migratedSnapshotCount(),
                retainedQuarantinedSnapshots,
                quarantineUpdate.newlyQuarantinedSnapshotIds(),
                nativeCompatibleLegacySnapshots,
                quarantineUpdate.clearedQuarantinedSnapshotIds()
        );
        MasuCraftFixes.LOGGER.warn(
                "Completed Vault v1_67 disk migration: {} active record(s), {} snapshot file(s), "
                        + "{} retained quarantined snapshot file(s), {} native-compatible v1_66 file(s) unchanged, "
                        + "{} cleared quarantine ID(s). Backups: {}",
                migratedActiveRecords,
                snapshotMigrationResult.migratedSnapshotCount(),
                retainedQuarantinedSnapshots,
                nativeCompatibleLegacySnapshots,
                quarantineUpdate.clearedQuarantinedSnapshotIds(),
                backupDirectory
        );
    }

    private static MigrationInventory inspectMigrationInventory(
            Path activeVaultData,
            Path snapshotDirectory
    ) throws IOException {
        ActiveVaultInspection activeInspection = inspectActiveVaultData(activeVaultData);
        List<Path> snapshotFiles = listSnapshotFiles(snapshotDirectory);
        List<Path> legacySnapshotFiles = new ArrayList<>();
        long legacyBytes = activeInspection.legacyRecordCount() > 0 && Files.exists(activeVaultData)
                ? Files.size(activeVaultData)
                : 0L;
        long largestLegacyFile = legacyBytes;

        for (Path snapshotFile : snapshotFiles) {
            Version version = readSnapshotVersion(snapshotFile);
            if (version == Version.v1_66) {
                legacySnapshotFiles.add(snapshotFile);
                long fileSize = Files.size(snapshotFile);
                legacyBytes = Math.addExact(legacyBytes, fileSize);
                largestLegacyFile = Math.max(largestLegacyFile, fileSize);
            }
        }

        return new MigrationInventory(
                activeInspection.totalRecordCount(),
                activeInspection.legacyRecordCount(),
                snapshotFiles.size(),
                List.copyOf(legacySnapshotFiles),
                legacyBytes,
                largestLegacyFile
        );
    }

    private static ActiveVaultInspection inspectActiveVaultData(Path activeVaultData) throws IOException {
        if (Files.notExists(activeVaultData)) {
            return new ActiveVaultInspection(0, 0);
        }

        CompoundTag root = requireCompressedNbt(activeVaultData);
        ListTag vaults = requireActiveVaultList(root, activeVaultData);
        int legacyRecords = 0;

        for (int index = 0; index < vaults.size(); index++) {
            Tag entry = vaults.get(index);
            if (!(entry instanceof CompoundTag compound)) {
                throw new IOException(
                        "Unsupported active Vault entry type " + entry.getType().getName()
                                + " at index " + index + " in " + activeVaultData
                );
            }

            if (readActiveVaultVersion(compound, activeVaultData, index) == Version.v1_66) {
                legacyRecords++;
            }
        }

        return new ActiveVaultInspection(vaults.size(), legacyRecords);
    }

    private static List<Path> listSnapshotFiles(Path snapshotDirectory) throws IOException {
        if (Files.notExists(snapshotDirectory)) {
            return List.of();
        }
        if (!Files.isDirectory(snapshotDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Vault snapshot path is not a directory: " + snapshotDirectory);
        }

        List<Path> snapshotFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapshotDirectory, "*.dat")) {
            for (Path snapshotFile : stream) {
                if (!Files.isRegularFile(snapshotFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Vault snapshot is not a regular file: " + snapshotFile);
                }
                snapshotFiles.add(snapshotFile.toAbsolutePath().normalize());
            }
        }
        snapshotFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return snapshotFiles;
    }

    private static Set<UUID> findVerifiedMigratedSnapshotIds(
            Path snapshotDirectory,
            Path backupDirectory
    ) throws IOException {
        Path snapshotBackupDirectory = backupDirectory.resolve(SNAPSHOT_DIRECTORY);
        if (Files.notExists(snapshotBackupDirectory)) {
            return Set.of();
        }

        Set<UUID> migratedSnapshotIds = new HashSet<>();
        for (Path backupSnapshot : listSnapshotFiles(snapshotBackupDirectory)) {
            if (readSnapshotVersion(backupSnapshot) != Version.v1_66) {
                continue;
            }

            Path currentSnapshot = snapshotDirectory.resolve(backupSnapshot.getFileName()).normalize();
            if (Files.notExists(currentSnapshot) || readSnapshotVersion(currentSnapshot) != Version.v1_67) {
                continue;
            }

            CompoundTag currentTag = requireUncompressedNbt(currentSnapshot);
            VaultSnapshot snapshot = readSnapshot(currentTag, Version.v1_67, currentSnapshot);
            if (snapshot.getVersion() != Version.v1_67) {
                throw new IOException("Migrated snapshot did not decode as v1_67: " + currentSnapshot);
            }

            String filename = backupSnapshot.getFileName().toString();
            String idText = filename.substring(0, filename.length() - ".dat".length());
            try {
                migratedSnapshotIds.add(UUID.fromString(idText));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Migrated Vault snapshot filename is not a UUID: " + backupSnapshot, exception);
            }
        }
        return Set.copyOf(migratedSnapshotIds);
    }

    private static List<Path> findUnquarantinedLegacySnapshots(
            List<Path> legacySnapshotFiles,
            Set<UUID> quarantinedSnapshotIds
    ) throws IOException {
        List<Path> unquarantinedSnapshots = new ArrayList<>();
        for (Path snapshotFile : legacySnapshotFiles) {
            if (Files.exists(snapshotFile)
                    && readSnapshotVersion(snapshotFile) == Version.v1_66
                    && !quarantinedSnapshotIds.contains(readSnapshotId(snapshotFile))) {
                unquarantinedSnapshots.add(snapshotFile);
            }
        }
        return List.copyOf(unquarantinedSnapshots);
    }

    private static List<Path> findQuarantinedLegacySnapshots(
            List<Path> legacySnapshotFiles,
            Set<UUID> quarantinedSnapshotIds
    ) throws IOException {
        List<Path> quarantinedSnapshots = new ArrayList<>();
        for (Path snapshotFile : legacySnapshotFiles) {
            if (quarantinedSnapshotIds.contains(readSnapshotId(snapshotFile))) {
                quarantinedSnapshots.add(snapshotFile);
            }
        }
        return List.copyOf(quarantinedSnapshots);
    }

    private static int countLegacySnapshotFiles(List<Path> snapshotFiles) throws IOException {
        int legacySnapshotFiles = 0;
        for (Path snapshotFile : snapshotFiles) {
            if (Files.exists(snapshotFile) && readSnapshotVersion(snapshotFile) == Version.v1_66) {
                legacySnapshotFiles++;
            }
        }
        return legacySnapshotFiles;
    }

    private static UUID readSnapshotId(Path snapshotFile) throws IOException {
        String filename = snapshotFile.getFileName().toString();
        if (!filename.endsWith(".dat")) {
            throw new IOException("Vault snapshot filename does not end in .dat: " + snapshotFile);
        }
        String idText = filename.substring(0, filename.length() - ".dat".length());
        try {
            return UUID.fromString(idText);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Vault snapshot filename is not a UUID: " + snapshotFile, exception);
        }
    }

    private static boolean hasQuarantinedSnapshotIds(
            Path snapshotManifest,
            Set<UUID> migratedSnapshotIds
    ) throws IOException {
        if (migratedSnapshotIds.isEmpty()) {
            return false;
        }
        Set<UUID> quarantinedSnapshotIds = readQuarantinedSnapshotIds(snapshotManifest);
        quarantinedSnapshotIds.retainAll(migratedSnapshotIds);
        return !quarantinedSnapshotIds.isEmpty();
    }

    private static SnapshotQuarantineUpdate updateSnapshotQuarantine(
            Path snapshotManifest,
            Path dataDirectory,
            Path backupDirectory,
            Set<UUID> migratedSnapshotIds,
            Map<UUID, String> decodeFailures
    ) throws IOException {
        if (migratedSnapshotIds.isEmpty() && decodeFailures.isEmpty()) {
            return new SnapshotQuarantineUpdate(0, 0);
        }
        if (Files.notExists(snapshotManifest)) {
            if (decodeFailures.isEmpty()) {
                return new SnapshotQuarantineUpdate(0, 0);
            }
            throw new IOException(
                    "Cannot quarantine " + decodeFailures.size()
                            + " undecodable Vault snapshot(s) because the manifest is missing: " + snapshotManifest
            );
        }

        Set<UUID> originalQuarantinedIds = readQuarantinedSnapshotIds(snapshotManifest);
        CompoundTag root = requireUncompressedNbt(snapshotManifest);
        CompoundTag data = requireSavedData(root, snapshotManifest);
        Set<UUID> removedSnapshotIds = new HashSet<>();
        Set<UUID> serializedSnapshotIds = new HashSet<>();
        boolean changed = false;

        ListTag retainedIds = new ListTag();
        if (data.contains("corrupted_snapshots")) {
            Tag corruptedSnapshotTag = data.get("corrupted_snapshots");
            if (!(corruptedSnapshotTag instanceof ListTag corruptedIds)
                    || (!corruptedIds.isEmpty() && corruptedIds.getElementType() != Tag.TAG_STRING)) {
                throw new IOException("Vault snapshot manifest has an invalid corrupted_snapshots tag: " + snapshotManifest);
            }
            for (int index = 0; index < corruptedIds.size(); index++) {
                String idText = corruptedIds.getString(index);
                UUID snapshotId;
                try {
                    snapshotId = UUID.fromString(idText);
                } catch (IllegalArgumentException ignored) {
                    retainedIds.add(corruptedIds.get(index).copy());
                    continue;
                }

                if (migratedSnapshotIds.contains(snapshotId)) {
                    removedSnapshotIds.add(snapshotId);
                    changed = true;
                } else {
                    retainedIds.add(corruptedIds.get(index).copy());
                    serializedSnapshotIds.add(snapshotId);
                }
            }
        }
        for (UUID failedSnapshotId : decodeFailures.keySet()) {
            if (serializedSnapshotIds.add(failedSnapshotId)) {
                retainedIds.add(StringTag.valueOf(failedSnapshotId.toString()));
                changed = true;
            }
        }
        replaceOrRemoveList(data, "corrupted_snapshots", retainedIds);

        ListTag retainedReasons = new ListTag();
        Set<UUID> snapshotIdsWithReasons = new HashSet<>();
        if (data.contains("corrupted_snapshot_reasons")) {
            Tag corruptedReasonTag = data.get("corrupted_snapshot_reasons");
            if (!(corruptedReasonTag instanceof ListTag corruptedReasons)
                    || (!corruptedReasons.isEmpty() && corruptedReasons.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IOException(
                        "Vault snapshot manifest has an invalid corrupted_snapshot_reasons tag: " + snapshotManifest
                );
            }
            for (int index = 0; index < corruptedReasons.size(); index++) {
                CompoundTag reason = corruptedReasons.getCompound(index);
                if (reason.hasUUID("FileId") && migratedSnapshotIds.contains(reason.getUUID("FileId"))) {
                    removedSnapshotIds.add(reason.getUUID("FileId"));
                    changed = true;
                } else {
                    retainedReasons.add(reason.copy());
                    if (reason.hasUUID("FileId")) {
                        snapshotIdsWithReasons.add(reason.getUUID("FileId"));
                    }
                }
            }
        }
        for (Map.Entry<UUID, String> decodeFailure : decodeFailures.entrySet()) {
            if (snapshotIdsWithReasons.add(decodeFailure.getKey())) {
                CompoundTag reason = new CompoundTag();
                reason.putUUID("FileId", decodeFailure.getKey());
                reason.putString("Reason", trimCorruptionReason(decodeFailure.getValue()));
                retainedReasons.add(reason);
                changed = true;
            }
        }
        replaceOrRemoveList(data, "corrupted_snapshot_reasons", retainedReasons);

        if (!changed) {
            return new SnapshotQuarantineUpdate(0, 0);
        }

        root.put("data", data);
        backupSnapshotManifestOriginal(snapshotManifest, dataDirectory, backupDirectory);
        writeNbtAtomically(snapshotManifest, root, false, temporaryFile -> {
            Set<UUID> remainingIds = readQuarantinedSnapshotIds(temporaryFile);
            Set<UUID> migratedIdsStillQuarantined = new HashSet<>(remainingIds);
            migratedIdsStillQuarantined.retainAll(migratedSnapshotIds);
            if (!migratedIdsStillQuarantined.isEmpty()) {
                throw new IOException(
                        "Temporary Vault snapshot manifest still quarantines "
                                + migratedIdsStillQuarantined.size()
                                + " migrated snapshot ID(s): " + temporaryFile
                );
            }
            if (!remainingIds.containsAll(decodeFailures.keySet())) {
                Set<UUID> missingIds = new HashSet<>(decodeFailures.keySet());
                missingIds.removeAll(remainingIds);
                throw new IOException(
                        "Temporary Vault snapshot manifest is missing " + missingIds.size()
                                + " undecodable snapshot quarantine ID(s): " + temporaryFile
                );
            }
        });
        Set<UUID> newlyQuarantinedIds = new HashSet<>(decodeFailures.keySet());
        newlyQuarantinedIds.removeAll(originalQuarantinedIds);
        return new SnapshotQuarantineUpdate(newlyQuarantinedIds.size(), removedSnapshotIds.size());
    }

    private static Set<UUID> readQuarantinedSnapshotIds(Path snapshotManifest) throws IOException {
        if (Files.notExists(snapshotManifest)) {
            return new HashSet<>();
        }

        CompoundTag data = requireSavedData(requireUncompressedNbt(snapshotManifest), snapshotManifest);
        Set<UUID> snapshotIds = new HashSet<>();
        if (data.contains("corrupted_snapshots", Tag.TAG_LIST)) {
            ListTag corruptedIds = data.getList("corrupted_snapshots", Tag.TAG_STRING);
            for (int index = 0; index < corruptedIds.size(); index++) {
                try {
                    snapshotIds.add(UUID.fromString(corruptedIds.getString(index)));
                } catch (IllegalArgumentException ignored) {
                    // Vault itself preserves and ignores malformed quarantine entries.
                }
            }
        }
        if (data.contains("corrupted_snapshot_reasons", Tag.TAG_LIST)) {
            ListTag corruptedReasons = data.getList("corrupted_snapshot_reasons", Tag.TAG_COMPOUND);
            for (int index = 0; index < corruptedReasons.size(); index++) {
                CompoundTag reason = corruptedReasons.getCompound(index);
                if (reason.hasUUID("FileId")) {
                    snapshotIds.add(reason.getUUID("FileId"));
                }
            }
        }
        return snapshotIds;
    }

    private static void replaceOrRemoveList(CompoundTag data, String key, ListTag retainedValues) {
        if (retainedValues.isEmpty()) {
            data.remove(key);
        } else {
            data.put(key, retainedValues);
        }
    }

    private static int migrateActiveVaultData(
            Path activeVaultData,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        if (Files.notExists(activeVaultData)) {
            return 0;
        }

        CompoundTag root = requireCompressedNbt(activeVaultData);
        ListTag sourceVaults = requireActiveVaultList(root, activeVaultData);
        ListTag migratedVaults = new ListTag();
        int migratedRecords = 0;

        for (int index = 0; index < sourceVaults.size(); index++) {
            Tag entry = sourceVaults.get(index);
            if (!(entry instanceof CompoundTag compound)) {
                throw new IOException(
                        "Unsupported active Vault entry type " + entry.getType().getName()
                                + " at index " + index + " in " + activeVaultData
                );
            }

            Version sourceVersion = readActiveVaultVersion(compound, activeVaultData, index);
            if (sourceVersion == Version.v1_66) {
                Vault vault = readActiveVault(compound, sourceVersion, activeVaultData, index);
                if (!VaultV166Compatibility.promoteLegacyVault(vault)) {
                    throw new IOException("Failed to promote active Vault entry " + index + " from v1_66");
                }
                migratedVaults.add(writeActiveVault(vault));
                migratedRecords++;
            } else {
                migratedVaults.add(compound.copy());
            }
        }

        if (migratedRecords == 0) {
            return 0;
        }

        CompoundTag data = root.getCompound("data");
        data.put("vaults", migratedVaults);
        root.put("data", data);

        backupOriginal(activeVaultData, dataDirectory, backupDirectory);
        writeNbtAtomically(activeVaultData, root, true, temporaryFile -> {
            ActiveVaultInspection inspection = inspectActiveVaultData(temporaryFile);
            if (inspection.totalRecordCount() != sourceVaults.size() || inspection.legacyRecordCount() != 0) {
                throw new IOException(
                        "Temporary active Vault migration verification failed: records="
                                + inspection.totalRecordCount() + ", legacy=" + inspection.legacyRecordCount()
                );
            }
        });
        return migratedRecords;
    }

    private static SnapshotMigrationResult migrateSnapshotFiles(
            List<Path> legacySnapshotFiles,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        int migratedSnapshots = 0;
        Map<UUID, String> decodeFailures = new LinkedHashMap<>();
        int processedSnapshots = 0;
        for (Path snapshotFile : legacySnapshotFiles) {
            try {
                migrateSnapshotFile(snapshotFile, dataDirectory, backupDirectory);
                migratedSnapshots++;
            } catch (LegacySnapshotDecodeException exception) {
                UUID snapshotId = readSnapshotId(snapshotFile);
                decodeFailures.put(
                        snapshotId,
                        "MasuCraftFixes v1_66 migration could not decode snapshot: "
                                + summarizeException(exception)
                );
                MasuCraftFixes.LOGGER.warn(
                        "Retaining legacy Vault snapshot {} and recording it in Vault's corruption quarantine "
                                + "because it could not be decoded: {}",
                        snapshotFile,
                        summarizeException(exception)
                );
                MasuCraftFixes.LOGGER.debug("Legacy Vault snapshot decode failure", exception);
            }
            processedSnapshots++;
            if (processedSnapshots == 1
                    || processedSnapshots % PROGRESS_INTERVAL == 0
                    || processedSnapshots == legacySnapshotFiles.size()) {
                MasuCraftFixes.LOGGER.info(
                        "Processed {} of {} legacy Vault snapshot file(s): {} migrated, {} retained for quarantine",
                        processedSnapshots,
                        legacySnapshotFiles.size(),
                        migratedSnapshots,
                        decodeFailures.size()
                );
            }
        }
        return new SnapshotMigrationResult(migratedSnapshots, Map.copyOf(decodeFailures));
    }

    private static void migrateSnapshotFile(
            Path snapshotFile,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        CompoundTag originalTag = requireUncompressedNbt(snapshotFile);
        Version sourceVersion = readSnapshotVersion(originalTag, snapshotFile);
        if (sourceVersion != Version.v1_66) {
            return;
        }

        VaultSnapshot snapshot;
        try {
            snapshot = readSnapshot(originalTag, sourceVersion, snapshotFile);
        } catch (IOException exception) {
            throw new LegacySnapshotDecodeException(snapshotFile, exception);
        }
        if (snapshot.getVersion() != Version.v1_67) {
            throw new IOException(
                    "Snapshot compatibility reader did not promote " + snapshotFile
                            + " to v1_67; found " + snapshot.getVersion()
            );
        }
        verifyNestedVaultVersion(snapshot.getStart(), "start", snapshotFile);
        verifyNestedVaultVersion(snapshot.getEnd(), "end", snapshotFile);

        KeyIndexResolver<Version> resolver = snapshot.getResolver();
        if (resolver == null || resolver.getVersion() != Version.v1_67) {
            throw new IOException("Snapshot has no v1_67 resolver after promotion: " + snapshotFile);
        }

        CompoundTag migratedTag = originalTag.copy();
        migratedTag.putLongArray("Snapshot", snapshot.getCache());
        migratedTag.put("SnapshotKeys", resolver.writeNbt());

        backupOriginal(snapshotFile, dataDirectory, backupDirectory);
        writeNbtAtomically(snapshotFile, migratedTag, false, temporaryFile -> {
            CompoundTag verificationTag = requireUncompressedNbt(temporaryFile);
            Version writtenVersion = readSnapshotVersion(verificationTag, temporaryFile);
            if (writtenVersion != Version.v1_67) {
                throw new IOException(
                        "Temporary snapshot migration verification found "
                                + writtenVersion + " instead of v1_67 in " + temporaryFile
                );
            }
            if (!verificationTag.contains("SnapshotKeys", Tag.TAG_LIST)) {
                throw new IOException("Temporary migrated snapshot has no SnapshotKeys: " + temporaryFile);
            }
        });
    }

    private static void verifyNestedVaultVersion(Vault vault, String position, Path snapshotFile) throws IOException {
        if (vault == null) {
            return;
        }
        if (!vault.has(Vault.VERSION)) {
            throw new IOException("Snapshot " + position + " Vault has no version: " + snapshotFile);
        }
        if (vault.get(Vault.VERSION) == Version.v1_66) {
            throw new IOException(
                    "Snapshot " + position + " Vault retained the ambiguous v1_66 version: " + snapshotFile
            );
        }
    }

    private static Version readSnapshotVersion(Path snapshotFile) throws IOException {
        return readSnapshotVersion(requireUncompressedNbt(snapshotFile), snapshotFile);
    }

    private static Version readSnapshotVersion(CompoundTag tag, Path snapshotFile) throws IOException {
        if (!tag.contains("Snapshot", Tag.TAG_LONG_ARRAY)) {
            throw new IOException("Vault snapshot has no Snapshot long array: " + snapshotFile);
        }

        long[] cache = tag.getLongArray("Snapshot");
        try {
            return ArrayBitBuffer.backing(cache, 0).readEnum(Version.class);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to read Vault snapshot version from " + snapshotFile, exception);
        }
    }

    private static VaultSnapshot readSnapshot(
            CompoundTag tag,
            Version version,
            Path snapshotFile
    ) throws IOException {
        long[] cache = tag.getLongArray("Snapshot");
        KeyIndexResolver<Version> resolver;
        try {
            resolver = tag.contains("SnapshotKeys", Tag.TAG_LIST)
                    ? KeyIndexResolver.fromTag(Vault.FIELDS, version, tag.getList("SnapshotKeys", Tag.TAG_STRING))
                    : KeyIndexResolver.of(Vault.FIELDS, version);
            return new VaultSnapshot(ArrayBitBuffer.backing(cache, 0), resolver);
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to deserialize legacy Vault snapshot " + snapshotFile + " as " + version,
                    exception
            );
        }
    }

    private static String summarizeException(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String trimCorruptionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String trimmed = reason.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.length() <= 1500 ? trimmed : trimmed.substring(0, 1497) + "...";
    }

    private static Version readActiveVaultVersion(
            CompoundTag serializedVault,
            Path activeVaultData,
            int index
    ) throws IOException {
        Version version = Version.fromName(serializedVault.getString("Version"));
        if (version != null) {
            return version;
        }
        if (!serializedVault.contains("Data", Tag.TAG_LONG_ARRAY)) {
            throw new IOException("Active Vault entry " + index + " has no Data long array in " + activeVaultData);
        }

        try {
            return ArrayBitBuffer.backing(serializedVault.getLongArray("Data"), 0).readEnum(Version.class);
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to read version for active Vault entry " + index + " in " + activeVaultData,
                    exception
            );
        }
    }

    private static Vault readActiveVault(
            CompoundTag serializedVault,
            Version version,
            Path activeVaultData,
            int index
    ) throws IOException {
        if (!serializedVault.contains("Data", Tag.TAG_LONG_ARRAY)) {
            throw new IOException("Active Vault entry " + index + " has no Data long array in " + activeVaultData);
        }

        ArrayBitBuffer buffer = ArrayBitBuffer.backing(serializedVault.getLongArray("Data"), 0);
        try {
            Version encodedVersion = buffer.readEnum(Version.class);
            if (encodedVersion != version) {
                throw new IOException(
                        "Active Vault entry " + index + " version mismatch: tag="
                                + version + ", bit stream=" + encodedVersion
                );
            }

            KeyIndexResolver<Version> resolver = serializedVault.contains("Keys", Tag.TAG_LIST)
                    ? KeyIndexResolver.fromTag(Vault.FIELDS, version, serializedVault.getList("Keys", Tag.TAG_STRING))
                    : KeyIndexResolver.of(Vault.FIELDS, version);
            RegistryIndexSyncContext<Version> context = new RegistryIndexSyncContext<>(version)
                    .with(Vault.FIELDS, resolver);
            return new Vault().read(buffer, context);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Failed to deserialize active Vault entry " + index + " in " + activeVaultData,
                    exception
            );
        }
    }

    private static CompoundTag writeActiveVault(Vault vault) throws IOException {
        Version version = vault.get(Vault.VERSION);
        if (version != Version.v1_67) {
            throw new IOException("Refusing to write active Vault with version " + version + "; expected v1_67");
        }

        KeyIndexResolver<Version> resolver = KeyIndexResolver.of(Vault.FIELDS, version);
        RegistryIndexSyncContext<Version> context = new RegistryIndexSyncContext<>(version)
                .with(Vault.FIELDS, resolver);
        ArrayBitBuffer buffer = ArrayBitBuffer.empty();
        buffer.writeEnum(version);
        vault.write(buffer, context);

        CompoundTag serializedVault = new CompoundTag();
        serializedVault.putString("Version", version.getName());
        serializedVault.put("Data", new LongArrayTag(buffer.toLongArray()));
        serializedVault.put("Keys", resolver.writeNbt());
        return serializedVault;
    }

    private static CompoundTag requireCompressedNbt(Path file) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(file.toFile());
        if (tag == null) {
            throw new IOException("Compressed NBT file returned null: " + file);
        }
        return tag;
    }

    private static CompoundTag requireUncompressedNbt(Path file) throws IOException {
        CompoundTag tag = NbtIo.read(file.toFile());
        if (tag == null) {
            throw new IOException("NBT file returned null: " + file);
        }
        return tag;
    }

    private static ListTag requireActiveVaultList(CompoundTag root, Path activeVaultData) throws IOException {
        CompoundTag data = requireSavedData(root, activeVaultData);
        Tag vaults = data.get("vaults");
        if (!(vaults instanceof ListTag list)) {
            throw new IOException("Active Vault saved data has no vaults list: " + activeVaultData);
        }
        return list;
    }

    private static CompoundTag requireSavedData(CompoundTag root, Path savedDataFile) throws IOException {
        if (!root.contains("data", Tag.TAG_COMPOUND)) {
            throw new IOException("Saved data file has no data compound: " + savedDataFile);
        }
        return root.getCompound("data");
    }

    private static void ensureBackupCapacity(
            Path dataDirectory,
            Path backupDirectory,
            Path snapshotManifest,
            MigrationInventory inventory,
            List<Path> legacySnapshotsToMigrate,
            boolean includeSnapshotManifest
    ) throws IOException {
        long missingBackupBytes = 0L;
        long largestFile = inventory.largestLegacyFile();
        Path activeVaultData = dataDirectory.resolve(ACTIVE_VAULT_DATA_FILE);
        if (inventory.legacyActiveVaultRecordCount() > 0) {
            Path activeBackup = backupPath(activeVaultData, dataDirectory, backupDirectory);
            if (Files.notExists(activeBackup)) {
                missingBackupBytes = Math.addExact(missingBackupBytes, Files.size(activeVaultData));
            }
        }
        for (Path snapshotFile : legacySnapshotsToMigrate) {
            Path snapshotBackup = backupPath(snapshotFile, dataDirectory, backupDirectory);
            if (Files.notExists(snapshotBackup)) {
                missingBackupBytes = Math.addExact(missingBackupBytes, Files.size(snapshotFile));
            }
        }
        if (includeSnapshotManifest && Files.exists(snapshotManifest)) {
            long manifestBytes = Files.size(snapshotManifest);
            largestFile = Math.max(largestFile, manifestBytes);
            Path manifestBackup = backupPath(snapshotManifest, dataDirectory, backupDirectory);
            if (Files.notExists(manifestBackup)) {
                missingBackupBytes = Math.addExact(missingBackupBytes, manifestBytes);
            }
        }

        long requiredBytes = Math.addExact(
                missingBackupBytes,
                Math.addExact(largestFile, FREE_SPACE_MARGIN_BYTES)
        );
        FileStore fileStore = Files.getFileStore(dataDirectory);
        long usableBytes = fileStore.getUsableSpace();
        if (usableBytes < requiredBytes) {
            throw new IOException(
                    "Insufficient free space for Vault migration backups: required="
                            + requiredBytes + ", usable=" + usableBytes + ", path=" + dataDirectory
            );
        }
    }

    private static void backupOriginal(
            Path source,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        Path backup = backupPath(source, dataDirectory, backupDirectory);
        if (Files.exists(backup)) {
            if (Files.mismatch(source, backup) != -1L) {
                throw new IOException(
                        "Existing migration backup does not match the still-legacy source file: " + backup
                );
            }
            return;
        }

        Files.createDirectories(backup.getParent());
        Path temporaryBackup = backup.resolveSibling(backup.getFileName() + TEMP_SUFFIX);
        Files.deleteIfExists(temporaryBackup);
        Files.copy(source, temporaryBackup, StandardCopyOption.COPY_ATTRIBUTES);
        forceFile(temporaryBackup);
        if (Files.mismatch(source, temporaryBackup) != -1L) {
            throw new IOException("Migration backup verification failed for " + source);
        }
        atomicReplace(temporaryBackup, backup);
        forceDirectory(backup.getParent());
    }

    private static void backupSnapshotManifestOriginal(
            Path snapshotManifest,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        Path backup = backupPath(snapshotManifest, dataDirectory, backupDirectory);
        if (Files.exists(backup)) {
            requireSavedData(requireUncompressedNbt(backup), backup);
            return;
        }
        backupOriginal(snapshotManifest, dataDirectory, backupDirectory);
    }

    private static Path backupPath(
            Path source,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (!normalizedSource.startsWith(dataDirectory)) {
            throw new IOException("Refusing to back up file outside Vault data directory: " + source);
        }
        return backupDirectory.resolve(dataDirectory.relativize(normalizedSource)).normalize();
    }

    private static void writeNbtAtomically(
            Path destination,
            CompoundTag tag,
            boolean compressed,
            PathValidator validator
    ) throws IOException {
        Path temporaryFile = destination.resolveSibling(destination.getFileName() + TEMP_SUFFIX);
        Files.deleteIfExists(temporaryFile);
        if (compressed) {
            NbtIo.writeCompressed(tag, temporaryFile.toFile());
        } else {
            NbtIo.write(tag, temporaryFile.toFile());
        }
        copyPosixPermissions(destination, temporaryFile);
        forceFile(temporaryFile);
        validator.validate(temporaryFile);
        atomicReplace(temporaryFile, destination);
        forceDirectory(destination.getParent());
    }

    private static void writeCompletionMarker(
            Path completionMarker,
            Path backupDirectory,
            MigrationInventory inventory,
            int migratedActiveRecords,
            int migratedSnapshots,
            int retainedQuarantinedSnapshots,
            int newlyQuarantinedSnapshotIds,
            int nativeCompatibleLegacySnapshots,
            int clearedQuarantinedSnapshotIds
    ) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "3");
        properties.setProperty("sourceSchema", "v1_66-build-6573");
        properties.setProperty("targetSchema", "v1_67");
        properties.setProperty("vaultVersion", VaultV166Compatibility.TARGET_VAULT_VERSION);
        properties.setProperty("completedAt", Instant.now().toString());
        properties.setProperty("activeVaultRecords", Integer.toString(inventory.activeVaultRecordCount()));
        properties.setProperty("snapshotFiles", Integer.toString(inventory.snapshotFileCount()));
        properties.setProperty("migratedActiveVaultRecords", Integer.toString(migratedActiveRecords));
        properties.setProperty("migratedSnapshotFiles", Integer.toString(migratedSnapshots));
        properties.setProperty("retainedQuarantinedSnapshotFiles", Integer.toString(retainedQuarantinedSnapshots));
        properties.setProperty("newlyQuarantinedSnapshotIds", Integer.toString(newlyQuarantinedSnapshotIds));
        properties.setProperty(
                "nativeCompatibleLegacySnapshotFiles",
                Integer.toString(nativeCompatibleLegacySnapshots)
        );
        properties.setProperty(
                "clearedQuarantinedSnapshotIds",
                Integer.toString(clearedQuarantinedSnapshotIds)
        );
        properties.setProperty("backupDirectory", backupDirectory.getFileName().toString());

        Path temporaryMarker = completionMarker.resolveSibling(completionMarker.getFileName() + TEMP_SUFFIX);
        Files.deleteIfExists(temporaryMarker);
        try (var output = Files.newOutputStream(
                temporaryMarker,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            properties.store(output, "MasuCraftFixes Vault schema migration");
        }
        forceFile(temporaryMarker);
        atomicReplace(temporaryMarker, completionMarker);
        forceDirectory(completionMarker.getParent());
    }

    private static void writeRestoreInstructions(Path backupDirectory) throws IOException {
        Path instructions = backupDirectory.resolve("RESTORE_INSTRUCTIONS.txt");
        if (Files.exists(instructions)) {
            return;
        }

        String content = """
                MasuCraftFixes Vault v1_66 backup

                These files are the originals from before the v1_66 to v1_67 migration.

                To restore:
                1. Stop the server.
                2. Restore the backed-up files to the same relative paths under world/data.
                3. Delete world/data/masucraftfixes-v166-to-v167.properties.
                4. Start only with a Vault build and compatibility configuration that can read build 6573 v1_66 data.

                Do not restore individual snapshot files while the server is running.
                """;
        Path temporaryInstructions = instructions.resolveSibling(instructions.getFileName() + TEMP_SUFFIX);
        Files.deleteIfExists(temporaryInstructions);
        Files.writeString(
                temporaryInstructions,
                content,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
        forceFile(temporaryInstructions);
        atomicReplace(temporaryInstructions, instructions);
        forceDirectory(instructions.getParent());
    }

    private static void atomicReplace(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException(
                    "Filesystem does not support atomic replacement for " + destination
                            + "; original file was left unchanged",
                    exception
            );
        }
    }

    private static void copyPosixPermissions(Path source, Path destination) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(destination, permissions);
        } catch (UnsupportedOperationException ignored) {
            MasuCraftFixes.LOGGER.debug("POSIX permissions are unavailable for {}", destination);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private record ActiveVaultInspection(int totalRecordCount, int legacyRecordCount) {
    }

    private record SnapshotMigrationResult(int migratedSnapshotCount, Map<UUID, String> decodeFailures) {
    }

    private record SnapshotQuarantineUpdate(
            int newlyQuarantinedSnapshotIds,
            int clearedQuarantinedSnapshotIds
    ) {
    }

    private record MigrationInventory(
            int activeVaultRecordCount,
            int legacyActiveVaultRecordCount,
            int snapshotFileCount,
            List<Path> legacySnapshotFiles,
            long legacyBytes,
            long largestLegacyFile
    ) {

        private boolean hasLegacyData() {
            return legacyActiveVaultRecordCount > 0 || !legacySnapshotFiles.isEmpty();
        }
    }

    @FunctionalInterface
    private interface PathValidator {

        void validate(Path path) throws IOException;
    }

    private static final class LegacySnapshotDecodeException extends IOException {

        private LegacySnapshotDecodeException(Path snapshotFile, IOException cause) {
            super("Failed to decode legacy Vault snapshot " + snapshotFile, cause);
        }
    }
}
