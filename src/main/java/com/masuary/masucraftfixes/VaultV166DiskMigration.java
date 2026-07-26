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
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class VaultV166DiskMigration {

    private static final String ACTIVE_VAULT_DATA_FILE = "the_vault_Vaults.dat";
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
        Path snapshotDirectory = dataDirectory.resolve(SNAPSHOT_DIRECTORY);
        Path backupDirectory = dataDirectory.resolve(BACKUP_DIRECTORY);
        Path completionMarker = dataDirectory.resolve(COMPLETION_MARKER);

        MigrationInventory inventory = inspectMigrationInventory(activeVaultData, snapshotDirectory);
        if (!inventory.hasLegacyData()) {
            if (Files.exists(completionMarker)) {
                MasuCraftFixes.LOGGER.info(
                        "Verified completed Vault v1_67 disk migration: {} active record(s), {} snapshot file(s)",
                        inventory.activeVaultRecordCount(),
                        inventory.snapshotFileCount()
                );
            } else {
                writeCompletionMarker(
                        completionMarker,
                        backupDirectory,
                        inventory,
                        0,
                        0
                );
                MasuCraftFixes.LOGGER.info(
                        "Vault data already uses v1_67 or another non-colliding schema; wrote migration marker"
                );
            }
            return;
        }

        if (Files.exists(completionMarker)) {
            MasuCraftFixes.LOGGER.warn(
                    "Vault migration marker exists but {} legacy active record(s) and {} legacy snapshot file(s) remain; resuming migration",
                    inventory.legacyActiveVaultRecordCount(),
                    inventory.legacySnapshotFiles().size()
            );
        }

        ensureBackupCapacity(dataDirectory, backupDirectory, inventory);
        Files.createDirectories(backupDirectory);
        writeRestoreInstructions(backupDirectory);

        MasuCraftFixes.LOGGER.warn(
                "Starting one-time Vault disk migration: {} active v1_66 record(s), {} v1_66 snapshot file(s), {} MiB",
                inventory.legacyActiveVaultRecordCount(),
                inventory.legacySnapshotFiles().size(),
                inventory.legacyBytes() / 1024L / 1024L
        );

        int migratedActiveRecords = migrateActiveVaultData(activeVaultData, dataDirectory, backupDirectory);
        int migratedSnapshots = migrateSnapshotFiles(
                inventory.legacySnapshotFiles(),
                dataDirectory,
                backupDirectory
        );

        MigrationInventory verifiedInventory = inspectMigrationInventory(activeVaultData, snapshotDirectory);
        if (verifiedInventory.hasLegacyData()) {
            throw new IOException(
                    "Post-migration verification found "
                            + verifiedInventory.legacyActiveVaultRecordCount()
                            + " active v1_66 record(s) and "
                            + verifiedInventory.legacySnapshotFiles().size()
                            + " v1_66 snapshot file(s)"
            );
        }

        writeCompletionMarker(
                completionMarker,
                backupDirectory,
                verifiedInventory,
                migratedActiveRecords,
                migratedSnapshots
        );
        MasuCraftFixes.LOGGER.warn(
                "Completed Vault v1_67 disk migration: {} active record(s), {} snapshot file(s). Backups: {}",
                migratedActiveRecords,
                migratedSnapshots,
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

    private static int migrateSnapshotFiles(
            List<Path> legacySnapshotFiles,
            Path dataDirectory,
            Path backupDirectory
    ) throws IOException {
        int migratedSnapshots = 0;
        for (Path snapshotFile : legacySnapshotFiles) {
            migrateSnapshotFile(snapshotFile, dataDirectory, backupDirectory);
            migratedSnapshots++;
            if (migratedSnapshots == 1
                    || migratedSnapshots % PROGRESS_INTERVAL == 0
                    || migratedSnapshots == legacySnapshotFiles.size()) {
                MasuCraftFixes.LOGGER.info(
                        "Migrated {} of {} legacy Vault snapshot file(s)",
                        migratedSnapshots,
                        legacySnapshotFiles.size()
                );
            }
        }
        return migratedSnapshots;
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

        VaultSnapshot snapshot = readSnapshot(originalTag, sourceVersion, snapshotFile);
        if (snapshot.getVersion() != Version.v1_67) {
            throw new IOException(
                    "Snapshot compatibility reader did not promote " + snapshotFile
                            + " to v1_67; found " + snapshot.getVersion()
            );
        }
        if (snapshot.getStart() != null && snapshot.getStart().get(Vault.VERSION) != Version.v1_67) {
            throw new IOException("Snapshot start Vault was not promoted to v1_67: " + snapshotFile);
        }
        if (snapshot.getEnd() != null && snapshot.getEnd().get(Vault.VERSION) != Version.v1_67) {
            throw new IOException("Snapshot end Vault was not promoted to v1_67: " + snapshotFile);
        }

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
        if (!root.contains("data", Tag.TAG_COMPOUND)) {
            throw new IOException("Active Vault saved data has no data compound: " + activeVaultData);
        }
        CompoundTag data = root.getCompound("data");
        Tag vaults = data.get("vaults");
        if (!(vaults instanceof ListTag list)) {
            throw new IOException("Active Vault saved data has no vaults list: " + activeVaultData);
        }
        return list;
    }

    private static void ensureBackupCapacity(
            Path dataDirectory,
            Path backupDirectory,
            MigrationInventory inventory
    ) throws IOException {
        long missingBackupBytes = 0L;
        Path activeVaultData = dataDirectory.resolve(ACTIVE_VAULT_DATA_FILE);
        if (inventory.legacyActiveVaultRecordCount() > 0) {
            Path activeBackup = backupPath(activeVaultData, dataDirectory, backupDirectory);
            if (Files.notExists(activeBackup)) {
                missingBackupBytes = Math.addExact(missingBackupBytes, Files.size(activeVaultData));
            }
        }
        for (Path snapshotFile : inventory.legacySnapshotFiles()) {
            Path snapshotBackup = backupPath(snapshotFile, dataDirectory, backupDirectory);
            if (Files.notExists(snapshotBackup)) {
                missingBackupBytes = Math.addExact(missingBackupBytes, Files.size(snapshotFile));
            }
        }

        long requiredBytes = Math.addExact(
                missingBackupBytes,
                Math.addExact(inventory.largestLegacyFile(), FREE_SPACE_MARGIN_BYTES)
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
            int migratedSnapshots
    ) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("sourceSchema", "v1_66-build-6574");
        properties.setProperty("targetSchema", "v1_67");
        properties.setProperty("vaultVersion", VaultV166Compatibility.TARGET_VAULT_VERSION);
        properties.setProperty("completedAt", Instant.now().toString());
        properties.setProperty("activeVaultRecords", Integer.toString(inventory.activeVaultRecordCount()));
        properties.setProperty("snapshotFiles", Integer.toString(inventory.snapshotFileCount()));
        properties.setProperty("migratedActiveVaultRecords", Integer.toString(migratedActiveRecords));
        properties.setProperty("migratedSnapshotFiles", Integer.toString(migratedSnapshots));
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
                4. Start only with a Vault build and compatibility configuration that can read build 6574 v1_66 data.

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
}
