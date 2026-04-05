package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.LuckPermsInt;
import com.masuary.masucraftfixes.RewardPermissions;
import iskallia.vault.www.Reward;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = Reward.class, remap = false)
public class RewardMixin {

    @Shadow
    private UUID id;

    @Inject(method = "hasModel", at = @At("RETURN"), cancellable = true)
    private void checkLuckPermsRewardPermission(Reward.ArmorPiece armor, ResourceLocation modelId, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            String permission = RewardPermissions.getPermissionForModel(modelId);
            if (permission != null && LuckPermsInt.hasPermission(id, permission)) {
                cir.setReturnValue(true);
            }
        }
    }
}
