package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.network.DisconnectHandOff;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Redirect(method = "disconnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void masucraftfixes$handOffDisconnectionOffTheServerThread(MinecraftServer server, Runnable handleDisconnection) {
        DisconnectHandOff.handleDisconnection(server, handleDisconnection, player.getGameProfile().getName());
    }
}
