package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.FtbEssentialsFlightCompatibility;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.expertise.type.AngelExpertise;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AngelExpertise.class, remap = false)
public class AngelExpertiseMixin {

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void skipOnTickWhenFtbFlyActive(SkillContext context, CallbackInfo ci) {
        Optional<ServerPlayer> playerOptional = context.getSource().as(ServerPlayer.class);
        if (playerOptional.isPresent()
                && FtbEssentialsFlightCompatibility.shouldPreserveFlightFromAngelExpertise(playerOptional.get())) {
            ci.cancel();
        }
    }
}
