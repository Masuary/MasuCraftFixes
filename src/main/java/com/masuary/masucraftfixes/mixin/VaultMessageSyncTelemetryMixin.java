package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultSyncTelemetry;
import iskallia.vault.core.data.sync.SyncMode;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.network.message.VaultMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VaultMessage.Sync.class, remap = false)
public abstract class VaultMessageSyncTelemetryMixin {
    @Shadow @Final private long[] payload;

    @Inject(
        method = "<init>(Lnet/minecraft/server/level/ServerPlayer;Liskallia/vault/core/vault/Vault;Liskallia/vault/core/data/sync/SyncMode;)V",
        at = @At("TAIL")
    )
    private void masucraftfixes$recordSync(ServerPlayer player, Vault vault, SyncMode mode, CallbackInfo ci) {
        long constructionNanos = VaultSyncTelemetry.finishConstructionTimer();
        int bytes = this.payload == null ? 0 : this.payload.length * Long.BYTES;
        VaultSyncTelemetry.recordSync(player, vault, mode, bytes, constructionNanos);
    }
}
