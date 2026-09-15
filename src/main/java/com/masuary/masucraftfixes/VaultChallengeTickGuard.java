package com.masuary.masucraftfixes;

public final class VaultChallengeTickGuard {
    public static final String TARGET_VAULT_VERSION = "1.18.2-3.21.6.6884";

    private VaultChallengeTickGuard() {
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
