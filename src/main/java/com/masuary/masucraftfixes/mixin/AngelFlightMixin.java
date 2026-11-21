package com.masuary.masucraftfixes.mixin;

import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import iskallia.vault.world.AngelFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AngelFlight.class, remap = false)
public class AngelFlightMixin {

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true)
    private static void skipAngelFlightWhenFtbFlyActive(TickEvent.PlayerTickEvent event, CallbackInfo callbackInfo) {
        if (event.player instanceof ServerPlayer serverPlayer) {
            FTBEPlayerData data = FTBEPlayerData.get(serverPlayer);
            if (data != null && data.fly) {
                callbackInfo.cancel();
            }
        }
    }
}
