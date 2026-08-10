package com.masuary.masucraftfixes;

import iskallia.vault.core.Version;
import iskallia.vault.core.data.Field;
import iskallia.vault.core.data.key.FieldKey;
import iskallia.vault.core.data.key.SupplierKey;
import iskallia.vault.core.data.key.registry.FieldRegistry;
import iskallia.vault.core.data.key.registry.SupplierRegistry;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultRegistry;
import iskallia.vault.core.vault.WaypointMarkers;
import iskallia.vault.core.vault.objective.ElixirObjective;
import iskallia.vault.core.vault.stat.MobsStat;
import iskallia.vault.core.vault.stat.StatCollector;
import iskallia.vault.core.world.generator.layout.GridLayout;
import iskallia.vault.core.world.generator.layout.VaultRoyaleLayout;
import net.minecraftforge.fml.ModList;

import java.util.function.Supplier;

public final class VaultV166Compatibility {

    public static final String TARGET_VAULT_VERSION = "1.18.2-3.21.6.6884";

    private static volatile boolean installed;
    private static long promotedSnapshotCount;

    private VaultV166Compatibility() {
    }

    public static void installIfConfigured() {
        if (!MasuCraftFixesConfig.ENABLE_LEGACY_V166_VAULT_DATA_COMPATIBILITY.get()) {
            MasuCraftFixes.LOGGER.info("Legacy Vault build 6573 v1_66 compatibility is disabled");
            return;
        }

        String installedVaultVersion = ModList.get()
                .getModContainerById("the_vault")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        if (!TARGET_VAULT_VERSION.equals(installedVaultVersion)) {
            MasuCraftFixes.LOGGER.info(
                    "Legacy Vault build 6573 v1_66 compatibility skipped for Vault version '{}'; expected '{}'",
                    installedVaultVersion,
                    TARGET_VAULT_VERSION
            );
            return;
        }

        try {
            restoreLegacyV166Schema();
            installed = true;
            MasuCraftFixes.LOGGER.warn(
                    "Installed the temporary Vault build 6573 v1_66 schema reader for "
                            + "the one-time Vault {} disk migration",
                    installedVaultVersion
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Refusing to start with an unexpected Vault v1_66 registry shape. "
                            + "Startup was aborted before Vault world data could be loaded.",
                    exception
            );
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static boolean promoteLegacyVault(Vault vault) {
        if (!installed || vault == null || !vault.has(Vault.VERSION) || vault.get(Vault.VERSION) != Version.v1_66) {
            return false;
        }

        vault.set(Vault.VERSION, Version.v1_67);
        return true;
    }

    public static synchronized void recordPromotedSnapshot() {
        promotedSnapshotCount++;
        if (promotedSnapshotCount == 1 || promotedSnapshotCount % 1000 == 0) {
            MasuCraftFixes.LOGGER.info(
                    "Decoded {} legacy Vault snapshot(s) for v1_67 disk migration",
                    promotedSnapshotCount
            );
        }
    }

    public static synchronized void restoreNativeSchemaAfterDiskMigration() {
        if (!installed) {
            return;
        }

        moveFieldDefinition(
                "MobsStat.Entry.KILLED_DUNGEON_BOSSES",
                MobsStat.Entry.KILLED_DUNGEON_BOSSES,
                MobsStat.Entry.FIELDS,
                Version.v1_67,
                Version.v1_66
        );
        moveFieldDefinition(
                "StatCollector.LIVE_STATS",
                StatCollector.LIVE_STATS,
                StatCollector.FIELDS,
                Version.v1_67,
                Version.v1_66
        );
        moveFieldDefinition(
                "WaypointMarkers.Marker.COLOUR_SLOT",
                WaypointMarkers.Marker.COLOUR_SLOT,
                WaypointMarkers.Marker.FIELDS,
                Version.v1_67,
                Version.v1_58
        );
        moveFieldDefinition(
                "WaypointMarkers.Marker.CREATED_AT",
                WaypointMarkers.Marker.CREATED_AT,
                WaypointMarkers.Marker.FIELDS,
                Version.v1_67,
                Version.v1_58
        );
        moveFieldDefinition(
                "ElixirObjective.TARGET_MULTIPLIER",
                ElixirObjective.TARGET_MULTIPLIER,
                ElixirObjective.FIELDS,
                Version.v1_66,
                Version.v1_67
        );
        moveSupplierDefinition(
                "VaultRoyaleLayout.KEY",
                VaultRoyaleLayout.KEY,
                VaultRegistry.GRID_LAYOUT,
                Version.v1_66,
                Version.v1_67
        );

        installed = false;
        MasuCraftFixes.LOGGER.warn(
                "Restored the native Vault {} schema after the on-disk v1_67 migration",
                TARGET_VAULT_VERSION
        );
    }

    private static void restoreLegacyV166Schema() {
        delayFieldUntilV167(
                "MobsStat.Entry.KILLED_DUNGEON_BOSSES",
                MobsStat.Entry.KILLED_DUNGEON_BOSSES,
                MobsStat.Entry.FIELDS,
                Version.v1_66
        );
        delayFieldUntilV167(
                "StatCollector.LIVE_STATS",
                StatCollector.LIVE_STATS,
                StatCollector.FIELDS,
                Version.v1_66
        );
        delayFieldUntilV167(
                "WaypointMarkers.Marker.COLOUR_SLOT",
                WaypointMarkers.Marker.COLOUR_SLOT,
                WaypointMarkers.Marker.FIELDS,
                Version.v1_58
        );
        delayFieldUntilV167(
                "WaypointMarkers.Marker.CREATED_AT",
                WaypointMarkers.Marker.CREATED_AT,
                WaypointMarkers.Marker.FIELDS,
                Version.v1_58
        );

        restoreFieldFromV166(
                "ElixirObjective.TARGET_MULTIPLIER",
                ElixirObjective.TARGET_MULTIPLIER,
                ElixirObjective.FIELDS
        );
        restoreSupplierFromV166(
                "VaultRoyaleLayout.KEY",
                VaultRoyaleLayout.KEY,
                VaultRegistry.GRID_LAYOUT
        );
    }

    private static <T> void delayFieldUntilV167(
            String fieldName,
            FieldKey<T> fieldKey,
            FieldRegistry registry,
            Version unexpectedIntroductionVersion
    ) {
        Field<T> field = requireSingleDefinition(fieldName, fieldKey, unexpectedIntroductionVersion);

        registry.remove(fieldKey);
        fieldKey.getMap().clear();
        fieldKey.getMap().put(Version.v1_67, field);
        registry.register(fieldKey);

        requireSupport(fieldName, fieldKey.supports(Version.v1_66), fieldKey.supports(Version.v1_67), false, true);
    }

    private static <T> void restoreFieldFromV166(String fieldName, FieldKey<T> fieldKey, FieldRegistry registry) {
        Field<T> field = requireSingleDefinition(fieldName, fieldKey, Version.v1_67);

        registry.remove(fieldKey);
        fieldKey.getMap().clear();
        fieldKey.getMap().put(Version.v1_66, field);
        registry.register(fieldKey);

        requireSupport(fieldName, fieldKey.supports(Version.v1_66), fieldKey.supports(Version.v1_67), true, true);
    }

    private static <T> void restoreSupplierFromV166(
            String keyName,
            SupplierKey<T> supplierKey,
            SupplierRegistry<T> registry
    ) {
        if (supplierKey.getMap().size() != 1 || !supplierKey.getMap().containsKey(Version.v1_67)) {
            throw new IllegalStateException(
                    keyName + " expected one v1_67 definition but found " + supplierKey.getMap().entrySet()
            );
        }

        Supplier<T> supplier = supplierKey.getMap().get(Version.v1_67);
        if (supplier == null) {
            throw new IllegalStateException(keyName + " has a null v1_67 supplier");
        }

        registry.remove(supplierKey);
        supplierKey.getMap().clear();
        supplierKey.getMap().put(Version.v1_66, supplier);
        registry.register(supplierKey);

        requireSupport(keyName, supplierKey.supports(Version.v1_66), supplierKey.supports(Version.v1_67), true, true);
    }

    private static <T> void moveFieldDefinition(
            String fieldName,
            FieldKey<T> fieldKey,
            FieldRegistry registry,
            Version sourceVersion,
            Version targetVersion
    ) {
        Field<T> field = requireSingleDefinition(fieldName, fieldKey, sourceVersion);

        registry.remove(fieldKey);
        fieldKey.getMap().clear();
        fieldKey.getMap().put(targetVersion, field);
        registry.register(fieldKey);

        if (!fieldKey.supports(Version.v1_67)) {
            throw new IllegalStateException(fieldName + " does not support v1_67 after native schema restoration");
        }
    }

    private static <T> void moveSupplierDefinition(
            String keyName,
            SupplierKey<T> supplierKey,
            SupplierRegistry<T> registry,
            Version sourceVersion,
            Version targetVersion
    ) {
        if (supplierKey.getMap().size() != 1 || !supplierKey.getMap().containsKey(sourceVersion)) {
            throw new IllegalStateException(
                    keyName + " expected one " + sourceVersion + " definition but found " + supplierKey.getMap().entrySet()
            );
        }

        Supplier<T> supplier = supplierKey.getMap().get(sourceVersion);
        if (supplier == null) {
            throw new IllegalStateException(keyName + " has a null " + sourceVersion + " supplier");
        }

        registry.remove(supplierKey);
        supplierKey.getMap().clear();
        supplierKey.getMap().put(targetVersion, supplier);
        registry.register(supplierKey);

        if (!supplierKey.supports(Version.v1_67)) {
            throw new IllegalStateException(keyName + " does not support v1_67 after native schema restoration");
        }
    }

    private static <T> Field<T> requireSingleDefinition(
            String fieldName,
            FieldKey<T> fieldKey,
            Version expectedVersion
    ) {
        if (fieldKey.getMap().size() != 1 || !fieldKey.getMap().containsKey(expectedVersion)) {
            throw new IllegalStateException(
                    fieldName + " expected one " + expectedVersion + " definition but found " + fieldKey.getMap().entrySet()
            );
        }

        Field<T> field = fieldKey.getMap().get(expectedVersion);
        if (field == null) {
            throw new IllegalStateException(fieldName + " has a null " + expectedVersion + " definition");
        }
        return field;
    }

    private static void requireSupport(
            String keyName,
            boolean supportsV166,
            boolean supportsV167,
            boolean expectedV166,
            boolean expectedV167
    ) {
        if (supportsV166 != expectedV166 || supportsV167 != expectedV167) {
            throw new IllegalStateException(
                    keyName + " support mismatch after compatibility install: v1_66="
                            + supportsV166 + ", v1_67=" + supportsV167
            );
        }
    }
}
