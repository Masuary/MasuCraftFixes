package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.LuckPermsInt;
import iskallia.vault.util.IskalliaDevs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = IskalliaDevs.class, remap = false)
public class IskalliaDevsMixin {

    private static final String DEVELOPER_PERMISSION = "masucraftfixes.developer";

    @Inject(method = "isDeveloper(Ljava/util/UUID;)Z", at = @At("RETURN"), cancellable = true)
    private static void onIsDeveloperByUuid(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && LuckPermsInt.hasPermission(uuid, DEVELOPER_PERMISSION)) {
            cir.setReturnValue(true);
        }
    }
}
