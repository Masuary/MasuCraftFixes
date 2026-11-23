package com.masuary.masucraftfixes.refinedstorage;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * Vault Integrations 1.0.22 ships its own Refined Storage chunk fix ({@code refinedStorageNoForcedChunkLoads}), which
 * redirects the same {@code INetworkNode.update()} call in {@code NetworkListener.onLevelTick} as
 * {@code NetworkListenerMixin}. With both active Mixin fails the boot with a bare injection error, so MasuCraftFixes
 * refuses to start instead and names the switch: our fix is kept because crafting-task carry-over, adoption of
 * offline-network nodes and reconnects build on it.
 */
public final class VaultIntegrationsChunkFixCheck {

    static final String STORAGE_FIX_CLASS_NAME = "iskallia.vaultintegrations.storage.StorageFix";
    static final String CONFLICTING_FIX_CONSTANT = "REFINED_STORAGE_NO_FORCED_CHUNK_LOADS";
    static final String CONFIG_FILE = "config/vaultintegrations/storage_fixes.json";
    static final String CONFIG_KEY = "refinedStorageNoForcedChunkLoads";

    private VaultIntegrationsChunkFixCheck() {
    }

    public static void requireConflictingFixInactive(ClassLoader classLoader) {
        requireConflictingFixInactive(findStorageFixClass(classLoader));
    }

    static void requireConflictingFixInactive(Optional<Class<?>> storageFixClass) {
        if (storageFixClass.isPresent() && isConflictingFixActive(storageFixClass.get())) {
            throw new IllegalStateException("MasuCraftFixes and Vault Integrations both patch Refined Storage's"
                    + " unloaded-chunk handling (NetworkListener.onLevelTick) and cannot run together. Set \""
                    + CONFIG_KEY + "\": {\"enabled\": false} in " + CONFIG_FILE + " and start again.");
        }
    }

    private static Optional<Class<?>> findStorageFixClass(ClassLoader classLoader) {
        try {
            return Optional.of(Class.forName(STORAGE_FIX_CLASS_NAME, false, classLoader));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        }
    }

    private static boolean isConflictingFixActive(Class<?> storageFixClass) {
        Object conflictingFix = readConflictingFix(storageFixClass);
        try {
            return (Boolean) storageFixClass.getMethod("isActive").invoke(conflictingFix);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new IllegalStateException("Vault Integrations could not tell whether " + CONFIG_KEY + " is active",
                    failure.getCause());
        } catch (NoSuchMethodException | IllegalAccessException | ClassCastException incompatible) {
            throw incompatibleVaultIntegrations(incompatible);
        }
    }

    private static Object readConflictingFix(Class<?> storageFixClass) {
        try {
            return storageFixClass.getField(CONFLICTING_FIX_CONSTANT).get(null);
        } catch (NoSuchFieldException | IllegalAccessException incompatible) {
            throw incompatibleVaultIntegrations(incompatible);
        }
    }

    private static IllegalStateException incompatibleVaultIntegrations(Exception cause) {
        return new IllegalStateException("MasuCraftFixes version guard: this Vault Integrations version has no readable "
                + STORAGE_FIX_CLASS_NAME + "." + CONFLICTING_FIX_CONSTANT + ".isActive(). Check whether its Refined"
                + " Storage chunk fix still redirects INetworkNode.update() in NetworkListener, then update"
                + " MasuCraftFixes for this Vault Integrations version.", cause);
    }
}
