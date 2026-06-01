package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.CasinoCraftAuditLogger;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "mod.casinocraft.network.MessageStateServer$Handler", remap = false)
public class CasinoCraftMessageStateServerHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), require = 0)
    private static void masucraftfixes$logStatePacket(@Coerce Object message, Supplier<NetworkEvent.Context> context, CallbackInfo ci) {
        CasinoCraftAuditLogger.logStatePacket(context);
    }
}
