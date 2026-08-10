package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultV166Compatibility;
import iskallia.vault.core.Version;
import iskallia.vault.core.data.key.registry.KeyIndexResolver;
import iskallia.vault.core.net.BitBuffer;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.stat.VaultSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VaultSnapshot.class, remap = false)
public abstract class VaultSnapshotV166CompatibilityMixin {

    @Shadow
    private Version version;

    @Shadow
    private Vault start;

    @Shadow
    private Vault end;

    @Shadow
    private long[] cache;

    @Shadow
    private KeyIndexResolver<Version> resolver;

    @Inject(method = "readBits", at = @At("RETURN"))
    private void promoteLegacySnapshot(BitBuffer buffer, CallbackInfo callbackInfo) {
        if (!VaultV166Compatibility.isInstalled() || version != Version.v1_66) {
            return;
        }

        VaultV166Compatibility.promoteLegacyVault(start);
        VaultV166Compatibility.promoteLegacyVault(end);
        version = Version.v1_67;
        resolver = KeyIndexResolver.of(Vault.FIELDS, Version.v1_67);
        cache = null;
        VaultV166Compatibility.recordPromotedSnapshot();
    }
}
