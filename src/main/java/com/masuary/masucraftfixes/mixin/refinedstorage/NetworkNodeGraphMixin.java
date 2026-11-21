package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.GraphRebuildDebt;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.network.NetworkNodeGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every graph rebuild starts at the controller's block entity, which loads the controller chunk. While it is unloaded
 * the rebuild is postponed (the network rebuilds once its controller is loaded and it can run again).
 */
@Mixin(value = NetworkNodeGraph.class, remap = false)
public abstract class NetworkNodeGraphMixin {

    @Shadow
    @Final
    private INetwork network;

    @Inject(method = "invalidate", at = @At("HEAD"), cancellable = true)
    private void postponeRebuildWhileControllerUnloaded(Action action, Level level, BlockPos origin, CallbackInfo callbackInfo) {
        if (level.isLoaded(origin)) {
            return;
        }
        if (network instanceof GraphRebuildDebt graphRebuildDebt) {
            graphRebuildDebt.masucraftfixes$oweGraphRebuild();
        }
        callbackInfo.cancel();
    }
}
