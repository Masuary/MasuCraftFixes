package com.masuary.masucraftfixes;

import java.util.Optional;

public final class VaultChallengeTickGuard {
    public static final String TARGET_VAULT_VERSION = "1.18.2-3.21.6.6884";

    private VaultChallengeTickGuard() {
    }

    /**
     * The guard only arms on its pinned Vault build, so a Vault update silently removes protection that
     * blocks stale challenge ticks daily on the live network. Empty when the guard is armed.
     */
    public static Optional<String> disarmedWarning(String installedVaultVersion) {
        if (TARGET_VAULT_VERSION.equals(installedVaultVersion)) {
            return Optional.empty();
        }
        return Optional.of("Vault challenge guard DISABLED: built for " + TARGET_VAULT_VERSION
                + ", running " + installedVaultVersion + ". Re-verify the synthetic tick lambda target"
                + " and re-pin VaultChallengeTickGuard.TARGET_VAULT_VERSION before relying on it.");
    }

    public static boolean canTick(Object attachedWorld, Object callbackWorld, Object registeredWorld,
                                  boolean challengeDeleted, boolean worldMarkedForDeletion) {
        // A dimension key can be reused; only the same live world instance is safe.
        return callbackWorld != null
                && attachedWorld == callbackWorld
                && registeredWorld == callbackWorld
                && !challengeDeleted
                && !worldMarkedForDeletion;
    }
}
