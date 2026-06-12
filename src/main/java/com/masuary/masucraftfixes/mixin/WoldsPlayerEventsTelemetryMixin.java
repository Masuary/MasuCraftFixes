package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.Stage0Telemetry;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xyz.iwolfking.woldsvaults.events.PlayerEvents", remap = false)
public abstract class WoldsPlayerEventsTelemetryMixin {
    @Inject(method = "onFilterNecklaceUse", at = @At("HEAD"), require = 0, remap = false)
    private static void masucraftfixes$stage0StartFilterNecklaceHandler(EntityItemPickupEvent event, CallbackInfo ci) {
        Stage0Telemetry.startWoldsFilterHandler(event);
    }

    @Inject(method = "onFilterNecklaceUse", at = @At("RETURN"), require = 0, remap = false)
    private static void masucraftfixes$stage0FinishFilterNecklaceHandler(EntityItemPickupEvent event, CallbackInfo ci) {
        Stage0Telemetry.finishWoldsFilterHandler(event);
    }
}
