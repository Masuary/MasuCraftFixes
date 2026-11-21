package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.permissions.PlayerLimitBypass;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedPlayerList.class)
public abstract class DedicatedPlayerListMixin {

    @Inject(method = "canBypassPlayerLimit", at = @At("RETURN"), cancellable = true)
    private void allowLuckPermsPlayerLimitBypass(GameProfile profile, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!callbackInfo.getReturnValueZ() && PlayerLimitBypass.canBypass(profile)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
