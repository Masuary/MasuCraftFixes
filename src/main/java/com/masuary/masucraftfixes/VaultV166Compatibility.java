package com.masuary.masucraftfixes;

import iskallia.vault.core.Version;
import iskallia.vault.core.data.DataMap;
import iskallia.vault.core.data.Field;
import iskallia.vault.core.data.ICompound;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.core.data.adapter.vault.CompoundAdapter;
import iskallia.vault.core.data.key.FieldKey;
import iskallia.vault.core.data.key.SupplierKey;
import iskallia.vault.core.data.key.registry.FieldRegistry;
import iskallia.vault.core.data.key.registry.SupplierRegistry;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultRegistry;
import iskallia.vault.core.vault.WaypointMarkers;
import iskallia.vault.core.vault.objective.ElixirObjective;
import iskallia.vault.core.vault.objective.Objective;
import iskallia.vault.core.vault.objective.VaultRoyaleObjective;
import iskallia.vault.core.vault.objective.VictoryObjective;
import iskallia.vault.core.vault.stat.MobsStat;
import iskallia.vault.core.vault.stat.StatCollector;
import iskallia.vault.core.world.generator.layout.DIYVaultLayout;
import iskallia.vault.core.world.generator.layout.GridLayout;
import iskallia.vault.core.world.generator.layout.VaultRoyaleLayout;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class VaultV166Compatibility {

    public static final String TARGET_VAULT_VERSION = "1.18.2-3.21.6.6884";
    private static final int MAXIMUM_MIGRATION_SEQUENCE_LENGTH = 65_536;
    private static final ThreadLocal<Integer> LEGACY_SNAPSHOT_DECODE_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static volatile boolean installed;
    private static LegacySnapshotSchema activeSchema;
    private static List<ExpandedFieldDefinition<?>> expandedFieldDefinitions = List.of();
    private static List<HistoricalFieldDefinition<?>> historicalAddonFieldDefinitions = List.of();
    private static List<HistoricalSupplierDefinition<?>> historicalAddonSupplierDefinitions = List.of();
    private static long promotedSnapshotCount;

    private VaultV166Compatibility() {
    }

    public static void installIfConfigured() {
        if (!MasuCraftFixesConfig.ENABLE_LEGACY_V166_VAULT_DATA_COMPATIBILITY.get()) {
            MasuCraftFixes.LOGGER.info("Legacy Vault v1_66 disk compatibility is disabled");
            return;
        }

        String installedVaultVersion = ModList.get()
                .getModContainerById("the_vault")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        if (!TARGET_VAULT_VERSION.equals(installedVaultVersion)) {
            MasuCraftFixes.LOGGER.info(
                    "Legacy Vault v1_66 disk compatibility skipped for Vault version '{}'; expected '{}'",
                    installedVaultVersion,
                    TARGET_VAULT_VERSION
            );
            return;
        }

        try {
            verifyNativeSchema();
            historicalAddonFieldDefinitions = captureHistoricalAddonFieldDefinitions();
            historicalAddonSupplierDefinitions = captureHistoricalAddonSupplierDefinitions();
            expandedFieldDefinitions = captureExpandedFieldDefinitions();
            installed = true;
            useLegacySnapshotSchema(LegacySnapshotSchema.BUILD_6573);
            MasuCraftFixes.LOGGER.warn(
                    "Installed the temporary Vault v1_66 schema recovery reader for "
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

    public static void beginLegacySnapshotDecode() {
        if (!installed) {
            throw new IllegalStateException("Vault v1_66 compatibility is not installed");
        }
        LEGACY_SNAPSHOT_DECODE_DEPTH.set(LEGACY_SNAPSHOT_DECODE_DEPTH.get() + 1);
    }

    public static void endLegacySnapshotDecode() {
        int depth = LEGACY_SNAPSHOT_DECODE_DEPTH.get();
        if (depth <= 1) {
            LEGACY_SNAPSHOT_DECODE_DEPTH.remove();
        } else {
            LEGACY_SNAPSHOT_DECODE_DEPTH.set(depth - 1);
        }
    }

    public static boolean isLegacySnapshotDecodeInProgress() {
        return installed && LEGACY_SNAPSHOT_DECODE_DEPTH.get() > 0;
    }

    public static int requireSafeSequenceLength(
            String sequenceType,
            int length,
            int remainingBits,
            int minimumBitsPerElement
    ) {
        if (!isLegacySnapshotDecodeInProgress()) {
            return length;
        }
        if (length < 0 || remainingBits < 0 || minimumBitsPerElement <= 0) {
            throw new IllegalStateException(
                    "Invalid " + sequenceType + " metadata during Vault migration: length="
                            + length + ", remainingBits=" + remainingBits
                            + ", minimumBitsPerElement=" + minimumBitsPerElement
            );
        }
        int maximumLengthFromInput = remainingBits / minimumBitsPerElement;
        if (length > MAXIMUM_MIGRATION_SEQUENCE_LENGTH || length > maximumLengthFromInput) {
            throw new IllegalStateException(
                    "Unsafe " + sequenceType + " length during Vault migration: " + length
                            + " (remainingBits=" + remainingBits
                            + ", maximum=" + Math.min(MAXIMUM_MIGRATION_SEQUENCE_LENGTH, maximumLengthFromInput)
                            + ")"
            );
        }
        return length;
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
                    "Decoded {} legacy Vault snapshot schema candidate(s) for v1_67 disk migration",
                    promotedSnapshotCount
            );
        }
    }

    public static synchronized void useLegacySnapshotSchema(LegacySnapshotSchema schema) {
        if (!installed) {
            throw new IllegalStateException("Vault v1_66 compatibility is not installed");
        }
        if (schema == activeSchema) {
            return;
        }

        setFieldIntroduction(
                "MobsStat.Entry.KILLED_DUNGEON_BOSSES",
                MobsStat.Entry.KILLED_DUNGEON_BOSSES,
                MobsStat.Entry.FIELDS,
                schema.includesDungeonBossStats() ? Version.v1_66 : Version.v1_67
        );
        setFieldIntroduction(
                "StatCollector.LIVE_STATS",
                StatCollector.LIVE_STATS,
                StatCollector.FIELDS,
                schema.includesLiveStats() ? Version.v1_66 : Version.v1_67
        );
        setFieldIntroduction(
                "WaypointMarkers.Marker.COLOUR_SLOT",
                WaypointMarkers.Marker.COLOUR_SLOT,
                WaypointMarkers.Marker.FIELDS,
                schema.includesWaypointMetadata() ? Version.v1_58 : Version.v1_67
        );
        setFieldIntroduction(
                "WaypointMarkers.Marker.CREATED_AT",
                WaypointMarkers.Marker.CREATED_AT,
                WaypointMarkers.Marker.FIELDS,
                schema.includesWaypointMetadata() ? Version.v1_58 : Version.v1_67
        );
        setFieldIntroduction(
                "ElixirObjective.TARGET_MULTIPLIER",
                ElixirObjective.TARGET_MULTIPLIER,
                ElixirObjective.FIELDS,
                schema.includesRemovedV166Fields() ? Version.v1_66 : Version.v1_67
        );
        setSupplierIntroduction(
                "VaultRoyaleLayout.KEY",
                VaultRoyaleLayout.KEY,
                VaultRegistry.GRID_LAYOUT,
                schema.includesRemovedV166Fields() ? Version.v1_66 : Version.v1_67
        );
        setHistoricalAddonFieldSupport(schema.includesHistoricalAddonFields());
        setHistoricalAddonSupplierSupport(schema.includesHistoricalAddonSuppliers());
        setExpandedFieldSupport(schema.includesAllV167Fields());
        activeSchema = schema;
    }

    public static synchronized void refreshExpandedSnapshotSchema() {
        if (!installed) {
            throw new IllegalStateException("Vault v1_66 compatibility is not installed");
        }

        useLegacySnapshotSchema(LegacySnapshotSchema.NATIVE_6884);
        expandedFieldDefinitions = captureExpandedFieldDefinitions();
        useLegacySnapshotSchema(LegacySnapshotSchema.BUILD_6573);
        MasuCraftFixes.LOGGER.info(
                "Prepared expanded v1_66 recovery support for {} v1_67-only field definition(s): {}",
                expandedFieldDefinitions.size(),
                expandedFieldDefinitions.stream()
                        .map(ExpandedFieldDefinition::name)
                        .sorted()
                        .toList()
        );
    }

    public static synchronized void restoreNativeSchemaAfterDiskMigration() {
        if (!installed) {
            return;
        }

        useLegacySnapshotSchema(LegacySnapshotSchema.NATIVE_6884);
        installed = false;
        LEGACY_SNAPSHOT_DECODE_DEPTH.remove();
        activeSchema = null;
        expandedFieldDefinitions = List.of();
        historicalAddonFieldDefinitions = List.of();
        historicalAddonSupplierDefinitions = List.of();
        MasuCraftFixes.LOGGER.warn(
                "Restored the native Vault {} schema after the on-disk v1_67 migration",
                TARGET_VAULT_VERSION
        );
    }

    private static void verifyNativeSchema() {
        requireSingleDefinition(
                "MobsStat.Entry.KILLED_DUNGEON_BOSSES",
                MobsStat.Entry.KILLED_DUNGEON_BOSSES,
                Version.v1_66
        );
        requireSingleDefinition("StatCollector.LIVE_STATS", StatCollector.LIVE_STATS, Version.v1_66);
        requireSingleDefinition(
                "WaypointMarkers.Marker.COLOUR_SLOT",
                WaypointMarkers.Marker.COLOUR_SLOT,
                Version.v1_58
        );
        requireSingleDefinition(
                "WaypointMarkers.Marker.CREATED_AT",
                WaypointMarkers.Marker.CREATED_AT,
                Version.v1_58
        );
        requireSingleDefinition(
                "ElixirObjective.TARGET_MULTIPLIER",
                ElixirObjective.TARGET_MULTIPLIER,
                Version.v1_67
        );
        requireSingleSupplierDefinition("VaultRoyaleLayout.KEY", VaultRoyaleLayout.KEY, Version.v1_67);
    }

    private static <T> void setFieldIntroduction(
            String fieldName,
            FieldKey<T> fieldKey,
            FieldRegistry registry,
            Version introductionVersion
    ) {
        Field<T> field = requireOnlyDefinition(fieldName, fieldKey);
        registry.remove(fieldKey);
        fieldKey.getMap().clear();
        fieldKey.getMap().put(introductionVersion, field);
        registry.register(fieldKey);

        boolean expectedV166Support = introductionVersion.compareTo(Version.v1_66) <= 0;
        requireSupport(
                fieldName,
                fieldKey.supports(Version.v1_66),
                fieldKey.supports(Version.v1_67),
                expectedV166Support,
                true
        );
    }

    private static <T> void setSupplierIntroduction(
            String keyName,
            SupplierKey<T> supplierKey,
            SupplierRegistry<T> registry,
            Version introductionVersion
    ) {
        Supplier<T> supplier = requireOnlySupplierDefinition(keyName, supplierKey);
        registry.remove(supplierKey);
        supplierKey.getMap().clear();
        supplierKey.getMap().put(introductionVersion, supplier);
        registry.register(supplierKey);

        boolean expectedV166Support = introductionVersion.compareTo(Version.v1_66) <= 0;
        requireSupport(
                keyName,
                supplierKey.supports(Version.v1_66),
                supplierKey.supports(Version.v1_67),
                expectedV166Support,
                true
        );
    }

    private static List<ExpandedFieldDefinition<?>> captureExpandedFieldDefinitions() {
        List<ExpandedFieldDefinition<?>> definitions = new ArrayList<>();
        captureExpandedFieldDefinitions("StatCollector", StatCollector.FIELDS, definitions);
        captureExpandedFieldDefinitions("MobsStat.Entry", MobsStat.Entry.FIELDS, definitions);
        captureExpandedFieldDefinitions("VictoryObjective", VictoryObjective.FIELDS, definitions);
        captureExpandedFieldDefinitions("VaultRoyaleObjective", VaultRoyaleObjective.FIELDS, definitions);
        captureExpandedFieldDefinitions("DIYVaultLayout", DIYVaultLayout.FIELDS, definitions);
        captureExpandedFieldDefinitions("ElixirObjective", ElixirObjective.FIELDS, definitions);
        return List.copyOf(definitions);
    }

    private static List<HistoricalFieldDefinition<?>> captureHistoricalAddonFieldDefinitions() {
        return List.of(
                captureHistoricalAddonFieldDefinition(
                        "Unobtainium BARREL_STATS",
                        requireResourceLocation("unobtanium:zzz_barrel_stats")
                ),
                captureHistoricalAddonFieldDefinition(
                        "Unobtainium CHEST_STATS",
                        requireResourceLocation("unobtanium:zzz_chest_stats")
                )
        );
    }

    private static ResourceLocation requireResourceLocation(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("Invalid resource location: " + id);
        }
        return resourceLocation;
    }

    private static HistoricalFieldDefinition<?> captureHistoricalAddonFieldDefinition(
            String fieldName,
            ResourceLocation fieldId
    ) {
        FieldKey<?> existingFieldKey = StatCollector.FIELDS.getKey(fieldId);
        if (existingFieldKey != null) {
            if (!existingFieldKey.supports(Version.v1_66)) {
                throw new IllegalStateException(fieldName + " exists but does not support v1_66");
            }
            return new HistoricalFieldDefinition<>(
                    fieldName,
                    existingFieldKey,
                    StatCollector.FIELDS,
                    true
            );
        }

        FieldKey<LegacyVaultStatHistogram> compatibilityFieldKey = FieldKey.of(
                        fieldId,
                        LegacyVaultStatHistogram.class
                )
                .with(
                        Version.v1_65,
                        CompoundAdapter.of(LegacyVaultStatHistogram::new),
                        ICompound.DISK.all().or(ICompound.CLIENT.all())
                );
        return new HistoricalFieldDefinition<>(
                fieldName,
                compatibilityFieldKey,
                StatCollector.FIELDS,
                false
        );
    }

    private static void setHistoricalAddonFieldSupport(boolean enabled) {
        for (HistoricalFieldDefinition<?> definition : historicalAddonFieldDefinitions) {
            boolean shouldBeRegistered = enabled || definition.registeredNatively();
            boolean isRegistered = definition.registry().contains(definition.fieldKey());
            if (shouldBeRegistered == isRegistered) {
                continue;
            }
            if (shouldBeRegistered) {
                definition.registry().register(definition.fieldKey());
            } else {
                definition.registry().remove(definition.fieldKey());
            }
        }
    }

    private static List<HistoricalSupplierDefinition<?>> captureHistoricalAddonSupplierDefinitions() {
        if (!ModList.get().isLoaded("woldsvaults")) {
            return List.of();
        }

        return List.of(
                captureHistoricalAddonSupplierDefinition(
                        "Wolds SurvivalObjective.KEY",
                        "xyz.iwolfking.woldsvaults.objectives.SurvivalObjective"
                ),
                captureHistoricalAddonSupplierDefinition(
                        "Wolds ZealotObjective.KEY",
                        "xyz.iwolfking.woldsvaults.objectives.ZealotObjective"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static HistoricalSupplierDefinition<Objective> captureHistoricalAddonSupplierDefinition(
            String keyName,
            String className
    ) {
        try {
            Class<?> objectiveClass = Class.forName(className);
            java.lang.reflect.Field keyField = objectiveClass.getField("KEY");
            Object value = keyField.get(null);
            if (!(value instanceof SupplierKey<?> supplierKey)) {
                throw new IllegalStateException(keyName + " is not a SupplierKey");
            }
            return captureHistoricalAddonSupplierDefinition(
                    keyName,
                    (SupplierKey<Objective>) supplierKey
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to inspect " + keyName, exception);
        }
    }

    private static HistoricalSupplierDefinition<Objective> captureHistoricalAddonSupplierDefinition(
            String keyName,
            SupplierKey<Objective> supplierKey
    ) {
        if (supplierKey.supports(Version.v1_66) || !supplierKey.supports(Version.v1_67)) {
            throw new IllegalStateException(
                    keyName + " expected native support only at v1_67 but found " + supplierKey.getMap().entrySet()
            );
        }
        Supplier<Objective> supplier = supplierKey.get(Version.v1_67);
        if (supplier == null) {
            throw new IllegalStateException(keyName + " has no v1_67 supplier");
        }
        return new HistoricalSupplierDefinition<>(
                keyName,
                supplierKey,
                VaultRegistry.OBJECTIVE,
                new LinkedHashMap<>(supplierKey.getMap()),
                supplier
        );
    }

    private static void setHistoricalAddonSupplierSupport(boolean enabled) {
        for (HistoricalSupplierDefinition<?> definition : historicalAddonSupplierDefinitions) {
            setHistoricalAddonSupplierSupport(definition, enabled);
        }
    }

    private static <T> void setHistoricalAddonSupplierSupport(
            HistoricalSupplierDefinition<T> definition,
            boolean enabled
    ) {
        definition.registry().remove(definition.supplierKey());
        definition.supplierKey().getMap().clear();
        if (enabled) {
            definition.supplierKey().getMap().put(Version.v1_66, definition.nativeV167Supplier());
        }
        definition.supplierKey().getMap().putAll(definition.nativeDefinitions());
        definition.registry().register(definition.supplierKey());

        requireSupport(
                definition.name(),
                definition.supplierKey().supports(Version.v1_66),
                definition.supplierKey().supports(Version.v1_67),
                enabled,
                true
        );
    }

    private static void captureExpandedFieldDefinitions(
            String registryName,
            FieldRegistry registry,
            List<ExpandedFieldDefinition<?>> definitions
    ) {
        for (FieldKey<?> fieldKey : registry.getKeys()) {
            if (fieldKey == ElixirObjective.TARGET_MULTIPLIER
                    || fieldKey.supports(Version.v1_66)
                    || !fieldKey.supports(Version.v1_67)) {
                continue;
            }
            captureExpandedFieldDefinition(registryName, registry, fieldKey, definitions);
        }
    }

    private static <T> void captureExpandedFieldDefinition(
            String registryName,
            FieldRegistry registry,
            FieldKey<T> fieldKey,
            List<ExpandedFieldDefinition<?>> definitions
    ) {
        definitions.add(new ExpandedFieldDefinition<>(
                registryName + "." + fieldKey.getId(),
                fieldKey,
                registry,
                new LinkedHashMap<>(fieldKey.getMap()),
                fieldKey.get(Version.v1_67)
        ));
    }

    private static void setExpandedFieldSupport(boolean enabled) {
        for (ExpandedFieldDefinition<?> definition : expandedFieldDefinitions) {
            setExpandedFieldSupport(definition, enabled);
        }
    }

    private static <T> void setExpandedFieldSupport(ExpandedFieldDefinition<T> definition, boolean enabled) {
        definition.registry().remove(definition.fieldKey());
        definition.fieldKey().getMap().clear();
        for (Version version : Version.values()) {
            if (enabled && version == Version.v1_66) {
                definition.fieldKey().getMap().put(Version.v1_66, definition.nativeV167Field());
            }
            if (definition.nativeDefinitions().containsKey(version)) {
                definition.fieldKey().getMap().put(version, definition.nativeDefinitions().get(version));
            }
        }
        definition.registry().register(definition.fieldKey());

        requireSupport(
                definition.name(),
                definition.fieldKey().supports(Version.v1_66),
                definition.fieldKey().supports(Version.v1_67),
                enabled,
                true
        );
    }

    private static <T> Field<T> requireSingleDefinition(
            String fieldName,
            FieldKey<T> fieldKey,
            Version expectedVersion
    ) {
        if (fieldKey.getMap().size() != 1 || !fieldKey.getMap().containsKey(expectedVersion)) {
            throw new IllegalStateException(
                    fieldName + " expected one " + expectedVersion + " definition but found "
                            + fieldKey.getMap().entrySet()
            );
        }
        return requireOnlyDefinition(fieldName, fieldKey);
    }

    private static <T> Field<T> requireOnlyDefinition(String fieldName, FieldKey<T> fieldKey) {
        if (fieldKey.getMap().size() != 1) {
            throw new IllegalStateException(
                    fieldName + " expected one definition but found " + fieldKey.getMap().entrySet()
            );
        }
        Field<T> field = fieldKey.getMap().values().iterator().next();
        if (field == null) {
            throw new IllegalStateException(fieldName + " has a null definition");
        }
        return field;
    }

    private static <T> Supplier<T> requireSingleSupplierDefinition(
            String keyName,
            SupplierKey<T> supplierKey,
            Version expectedVersion
    ) {
        if (supplierKey.getMap().size() != 1 || !supplierKey.getMap().containsKey(expectedVersion)) {
            throw new IllegalStateException(
                    keyName + " expected one " + expectedVersion + " definition but found "
                            + supplierKey.getMap().entrySet()
            );
        }
        return requireOnlySupplierDefinition(keyName, supplierKey);
    }

    private static <T> Supplier<T> requireOnlySupplierDefinition(String keyName, SupplierKey<T> supplierKey) {
        if (supplierKey.getMap().size() != 1) {
            throw new IllegalStateException(
                    keyName + " expected one definition but found " + supplierKey.getMap().entrySet()
            );
        }
        Supplier<T> supplier = supplierKey.getMap().values().iterator().next();
        if (supplier == null) {
            throw new IllegalStateException(keyName + " has a null supplier");
        }
        return supplier;
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
                    keyName + " support mismatch after schema selection: v1_66="
                            + supportsV166 + ", v1_67=" + supportsV167
            );
        }
    }

    public enum LegacySnapshotSchema {
        BUILD_6573("build-6573", false, false, false, true, true, true),
        LIVE_STATS_ONLY("live-stats-only", false, true, false, true, true, true),
        DUNGEON_STATS_ONLY("dungeon-stats-only", true, false, false, true, true, true),
        WAYPOINT_METADATA_ONLY("waypoint-metadata-only", false, false, true, true, true, true),
        LIVE_AND_DUNGEON_STATS("live-and-dungeon-stats", true, true, false, true, true, true),
        LIVE_STATS_AND_WAYPOINTS("live-stats-and-waypoints", false, true, true, true, true, true),
        DUNGEON_STATS_AND_WAYPOINTS("dungeon-stats-and-waypoints", true, false, true, true, true, true),
        LATE_V166("late-v166", true, true, true, true, true, true),
        EXPANDED_V166("expanded-v166", true, true, true, true, true, true, true),
        NATIVE_6884("native-6884", true, true, true, false, false, false);

        private final String markerName;
        private final boolean includesDungeonBossStats;
        private final boolean includesLiveStats;
        private final boolean includesWaypointMetadata;
        private final boolean includesRemovedV166Fields;
        private final boolean includesHistoricalAddonFields;
        private final boolean includesHistoricalAddonSuppliers;
        private final boolean includesAllV167Fields;

        LegacySnapshotSchema(
                String markerName,
                boolean includesDungeonBossStats,
                boolean includesLiveStats,
                boolean includesWaypointMetadata,
                boolean includesRemovedV166Fields,
                boolean includesHistoricalAddonFields,
                boolean includesHistoricalAddonSuppliers
        ) {
            this(
                    markerName,
                    includesDungeonBossStats,
                    includesLiveStats,
                    includesWaypointMetadata,
                    includesRemovedV166Fields,
                    includesHistoricalAddonFields,
                    includesHistoricalAddonSuppliers,
                    false
            );
        }

        LegacySnapshotSchema(
                String markerName,
                boolean includesDungeonBossStats,
                boolean includesLiveStats,
                boolean includesWaypointMetadata,
                boolean includesRemovedV166Fields,
                boolean includesHistoricalAddonFields,
                boolean includesHistoricalAddonSuppliers,
                boolean includesAllV167Fields
        ) {
            this.markerName = markerName;
            this.includesDungeonBossStats = includesDungeonBossStats;
            this.includesLiveStats = includesLiveStats;
            this.includesWaypointMetadata = includesWaypointMetadata;
            this.includesRemovedV166Fields = includesRemovedV166Fields;
            this.includesHistoricalAddonFields = includesHistoricalAddonFields;
            this.includesHistoricalAddonSuppliers = includesHistoricalAddonSuppliers;
            this.includesAllV167Fields = includesAllV167Fields;
        }

        public String markerName() {
            return markerName;
        }

        boolean includesHistoricalAddonFields() {
            return includesHistoricalAddonFields;
        }

        private boolean includesDungeonBossStats() {
            return includesDungeonBossStats;
        }

        private boolean includesLiveStats() {
            return includesLiveStats;
        }

        private boolean includesWaypointMetadata() {
            return includesWaypointMetadata;
        }

        private boolean includesRemovedV166Fields() {
            return includesRemovedV166Fields;
        }

        private boolean includesHistoricalAddonSuppliers() {
            return includesHistoricalAddonSuppliers;
        }

        private boolean includesAllV167Fields() {
            return includesAllV167Fields;
        }
    }

    private record ExpandedFieldDefinition<T>(
            String name,
            FieldKey<T> fieldKey,
            FieldRegistry registry,
            Map<Version, Field<T>> nativeDefinitions,
            Field<T> nativeV167Field
    ) {
    }

    private record HistoricalFieldDefinition<T>(
            String name,
            FieldKey<T> fieldKey,
            FieldRegistry registry,
            boolean registeredNatively
    ) {
    }

    private record HistoricalSupplierDefinition<T>(
            String name,
            SupplierKey<T> supplierKey,
            SupplierRegistry<T> registry,
            Map<Version, Supplier<T>> nativeDefinitions,
            Supplier<T> nativeV167Supplier
    ) {
    }

    private static final class LegacyVaultStatHistogram
            extends DataMap<LegacyVaultStatHistogram, Integer, Integer> {

        private LegacyVaultStatHistogram() {
            super(new HashMap<>(), Adapters.INT_SEGMENTED_7, Adapters.INT_SEGMENTED_7);
        }
    }
}
