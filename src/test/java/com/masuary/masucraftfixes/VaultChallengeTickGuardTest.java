package com.masuary.masucraftfixes;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VaultChallengeTickGuardTest {
    @Test
    public void liveAttachedWorldCanTickWithoutAnActiveVaultRecord() {
        Object world = new Object();
        assertTrue(VaultChallengeTickGuard.canTick(world, world, world, false, false));
    }

    @Test
    public void unloadedWorldCannotTickBeforeNativeChallengeCleanup() {
        Object world = new Object();
        assertFalse(VaultChallengeTickGuard.canTick(world, world, null, false, false));
    }

    @Test
    public void pendingWorldDeletionPreventsTickEvenWhileRegistered() {
        Object world = new Object();
        assertFalse(VaultChallengeTickGuard.canTick(world, world, world, false, true));
    }

    @Test
    public void replacementWorldWithTheSameDimensionDoesNotValidateAnOldCallback() {
        Object world = new String("the_vault:same_dimension");
        Object replacementWorld = new String("the_vault:same_dimension");
        assertFalse(VaultChallengeTickGuard.canTick(world, world, replacementWorld, false, false));
    }

    @Test
    public void alreadyDetachedCallbackCannotTick() {
        Object world = new Object();
        assertFalse(VaultChallengeTickGuard.canTick(null, world, world, false, false));
    }

    @Test
    public void reattachedManagerCannotTickItsPreviousWorld() {
        Object previousWorld = new Object();
        Object newWorld = new Object();
        assertFalse(VaultChallengeTickGuard.canTick(newWorld, previousWorld, previousWorld, false, false));
        assertTrue(VaultChallengeTickGuard.canTick(newWorld, newWorld, newWorld, false, false));
    }

    @Test
    public void deletedChallengeCannotTick() {
        Object world = new Object();
        assertFalse(VaultChallengeTickGuard.canTick(world, world, world, true, false));
    }

    @Test
    public void missingWorldReferencesCannotValidateEachOther() {
        assertFalse(VaultChallengeTickGuard.canTick(null, null, null, false, false));
    }
}
