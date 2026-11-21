package com.masuary.masucraftfixes.vault;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultDimensionsTest {

    private static final String VAULT_ID = "0f8e3c2a-1b2c-4d5e-8f90-a1b2c3d4e5f6";

    @Test
    void vaultHubAndRunningVaultsAreVaultDimensions() {
        assertTrue(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("the_vault", "vault")));
        assertTrue(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("the_vault", "vault_" + VAULT_ID)));
    }

    @Test
    void otherDimensionsAreNotVaultDimensions() {
        assertFalse(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("the_vault", "arena")));
        assertFalse(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("the_vault", "the_other_side")));
        assertFalse(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")));
        assertFalse(VaultDimensions.isVaultDimension(ResourceLocation.fromNamespaceAndPath("othermod", "vault")));
    }

    @Test
    void runningVaultWorldYieldsItsVaultId() {
        assertEquals(Optional.of(UUID.fromString(VAULT_ID)),
                VaultDimensions.vaultId(ResourceLocation.fromNamespaceAndPath("the_vault", "vault_" + VAULT_ID)));
    }

    @Test
    void nonVaultWorldsYieldNoVaultId() {
        assertEquals(Optional.empty(), VaultDimensions.vaultId(ResourceLocation.fromNamespaceAndPath("the_vault", "vault")));
        assertEquals(Optional.empty(), VaultDimensions.vaultId(ResourceLocation.fromNamespaceAndPath("the_vault", "vault_not-a-uuid")));
        assertEquals(Optional.empty(), VaultDimensions.vaultId(ResourceLocation.fromNamespaceAndPath("othermod", "vault_" + VAULT_ID)));
    }
}
