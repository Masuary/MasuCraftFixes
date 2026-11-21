package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.flight.FtbFlight;
import com.masuary.masucraftfixes.vault.VaultDimensions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void disableFlightInVault(CallbackInfo callbackInfo) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.hasDisconnected() || player.isDeadOrDying() || player.hasPermissions(4)) {
            return;
        }
        if (VaultDimensions.isVaultWorld(player.level)) {
            FtbFlight.disable(player);
        }
    }
}
