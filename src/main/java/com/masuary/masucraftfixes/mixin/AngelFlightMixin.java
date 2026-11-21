package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.flight.FtbFlight;
import com.masuary.masucraftfixes.vault.VaultDimensions;
import iskallia.vault.init.ModEffects;
import iskallia.vault.world.AngelFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vault Hunters revokes flight from Angel players who leave an Angel Block's range (tick) or regret the expertise
 * ({@code AngelExpertise.onRemove}). While FTB {@code /fly} is on, that would strip FTB's flight, so only VH's own
 * Angel state (effect and granted tag) is cleared and the flight stays.
 */
@Mixin(value = AngelFlight.class, remap = false)
public class AngelFlightMixin {

    @Shadow
    @Final
    private static String GRANTED_TAG;

    @Inject(method = "revoke", at = @At("HEAD"), cancellable = true)
    private static void keepFtbFlightWhenRevokingAngelFlight(Player player, CallbackInfo callbackInfo) {
        if (player instanceof ServerPlayer serverPlayer
                && FtbFlight.isActive(serverPlayer)
                && (serverPlayer.hasPermissions(4) || !VaultDimensions.isVaultWorld(serverPlayer.level))) {
            if (serverPlayer.hasEffect(ModEffects.ANGEL)) {
                serverPlayer.removeEffect(ModEffects.ANGEL);
            }
            serverPlayer.removeTag(GRANTED_TAG);
            callbackInfo.cancel();
        }
    }
}
