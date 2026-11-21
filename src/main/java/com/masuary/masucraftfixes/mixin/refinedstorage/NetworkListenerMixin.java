package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.RefinedStorageNodeTicking;
import com.masuary.masucraftfixes.refinedstorage.RefinedStorageReconnects;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.apiimpl.network.NetworkListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkListener.class, remap = false)
public abstract class NetworkListenerMixin {

    @Redirect(method = "onLevelTick", at = @At(value = "INVOKE",
            target = "Lcom/refinedmods/refinedstorage/api/network/node/INetworkNode;update()V"))
    private void tickOnlyLoadedNodes(INetworkNode node) {
        RefinedStorageNodeTicking.tickIfLoaded(node);
    }

    @Inject(method = "onLevelTick", at = @At("TAIL"))
    private void rebuildReconnectedNetworks(TickEvent.WorldTickEvent event, CallbackInfo callbackInfo) {
        if (event.phase == TickEvent.Phase.END && event.world instanceof ServerLevel serverLevel) {
            RefinedStorageReconnects.rebuildDueNetworks(serverLevel);
        }
    }
}
