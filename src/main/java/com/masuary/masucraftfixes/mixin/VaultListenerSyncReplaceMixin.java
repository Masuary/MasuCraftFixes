package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultSyncPolicy;
import iskallia.vault.core.data.sync.SyncMode;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.player.Listener;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.init.ModNetwork;
import iskallia.vault.network.message.VaultMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Listener.class, remap = false)
public abstract class VaultListenerSyncReplaceMixin {
    @Inject(method = "onJoin", at = @At("HEAD"))
    private void masucraftfixes$resetSyncOnJoin(VirtualWorld world, Vault vault, CallbackInfo ci) {
        VaultSyncPolicy.onListenerJoin((Listener)(Object)this, vault);
    }

    @Inject(method = "onLeave", at = @At("HEAD"))
    private void masucraftfixes$resetSyncOnLeave(VirtualWorld world, Vault vault, CallbackInfo ci) {
        VaultSyncPolicy.onListenerLeave((Listener)(Object)this, vault);
    }

    @Inject(
        method = "lambda$tickServer$2",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/server/level/ServerPlayer;Liskallia/vault/core/vault/Vault;Liskallia/vault/core/data/sync/SyncMode;)Liskallia/vault/network/message/VaultMessage$Sync;"
        ),
        cancellable = true
    )
    private static void masucraftfixes$replaceFullSync(Vault vault, VirtualWorld world, ServerPlayer player, CallbackInfo ci) {
        if (VaultSyncPolicy.shouldSkipOffworld(vault, world, player)) {
            ci.cancel();
            return;
        }

        if (VaultSyncPolicy.shouldSendVanillaFull(vault, player)) {
            return;
        }

        Vault hudVault = VaultSyncPolicy.createHudDiffVault(vault);
        ModNetwork.CHANNEL.sendTo(
            new VaultMessage.Sync(player, hudVault, SyncMode.DIFF),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT
        );
        ci.cancel();
    }
}
