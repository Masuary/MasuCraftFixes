package com.masuary.masucraftfixes.refinedstorage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultIntegrationsChunkFixCheckTest {

    enum ActiveStorageFix {
        REFINED_STORAGE_SAFE_LOADING,
        REFINED_STORAGE_NO_FORCED_CHUNK_LOADS;

        public boolean isActive() {
            return this == REFINED_STORAGE_NO_FORCED_CHUNK_LOADS;
        }
    }

    enum InactiveStorageFix {
        REFINED_STORAGE_NO_FORCED_CHUNK_LOADS;

        public boolean isActive() {
            return false;
        }
    }

    enum RenamedStorageFix {
        REFINED_STORAGE_UNLOADED_CHUNKS;

        public boolean isActive() {
            return true;
        }
    }

    static final IllegalStateException MALFORMED_CONFIG = new IllegalStateException("storage_fixes.json is malformed");

    enum MalformedConfigStorageFix {
        REFINED_STORAGE_NO_FORCED_CHUNK_LOADS;

        public boolean isActive() {
            throw MALFORMED_CONFIG;
        }
    }

    @Test
    void withoutVaultIntegrationsTheBootGoesOn() {
        assertDoesNotThrow(() -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(Optional.empty()));
    }

    @Test
    void classLoaderWithoutVaultIntegrationsCountsAsAbsent() {
        assertDoesNotThrow(() -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(
                VaultIntegrationsChunkFixCheckTest.class.getClassLoader()));
    }

    @Test
    void switchedOffVaultIntegrationsFixLetsTheBootGoOn() {
        assertDoesNotThrow(() -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(
                Optional.of(InactiveStorageFix.class)));
    }

    @Test
    void activeVaultIntegrationsFixStopsTheBootNamingTheFileAndKey() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(Optional.of(ActiveStorageFix.class)));

        assertTrue(failure.getMessage().contains("config/vaultintegrations/storage_fixes.json"), failure.getMessage());
        assertTrue(failure.getMessage().contains("\"refinedStorageNoForcedChunkLoads\": {\"enabled\": false}"),
                failure.getMessage());
    }

    @Test
    void vaultIntegrationsWithoutTheKnownFixStopsTheBoot() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(Optional.of(RenamedStorageFix.class)));

        assertTrue(failure.getMessage().contains("REFINED_STORAGE_NO_FORCED_CHUNK_LOADS"), failure.getMessage());
    }

    @Test
    void vaultIntegrationsConfigErrorIsPassedOnUnchanged() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(
                        Optional.of(MalformedConfigStorageFix.class)));

        assertSame(MALFORMED_CONFIG, failure);
    }
}
