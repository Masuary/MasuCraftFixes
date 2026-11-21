package com.masuary.masucraftfixes.vault;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VaultDimensions {

    private static final String VAULT_NAMESPACE = "the_vault";
    private static final String VAULT_PATH_PREFIX = "vault";
    private static final Pattern VAULT_WORLD_PATH = Pattern.compile(
            "vault_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private VaultDimensions() {
    }

    public static boolean isVaultWorld(Level level) {
        return isVaultDimension(level.dimension().location());
    }

    /** True for the vault hub ({@code the_vault:vault}) and every running vault world ({@code the_vault:vault_<uuid>}). */
    public static boolean isVaultDimension(ResourceLocation dimension) {
        return dimension.getNamespace().equals(VAULT_NAMESPACE) && dimension.getPath().startsWith(VAULT_PATH_PREFIX);
    }

    /** The vault id of a running vault world, empty for every other dimension. */
    public static Optional<UUID> vaultId(ResourceLocation dimension) {
        if (!dimension.getNamespace().equals(VAULT_NAMESPACE)) {
            return Optional.empty();
        }
        Matcher matcher = VAULT_WORLD_PATH.matcher(dimension.getPath());
        return matcher.matches() ? Optional.of(UUID.fromString(matcher.group(1))) : Optional.empty();
    }
}
