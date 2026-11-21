package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.GraphRebuildDebt;
import com.refinedmods.refinedstorage.apiimpl.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A network whose controller chunk is unloaded does not run at all: stock RS would read the controller's redstone
 * signal and rebuild its graph, both of which load the controller chunk (at server start, every network's chunk).
 * It counts as offline, as in stock RS, so its nodes and crafting requests stop, and it owes a graph rebuild that RS's
 * own 20-tick inactive-to-active transition performs once the controller is loaded again.
 */
@Mixin(value = Network.class, remap = false)
public abstract class NetworkMixin implements GraphRebuildDebt {

    @Shadow
    @Final
    private BlockPos pos;

    @Shadow
    @Final
    private Level level;

    @Shadow
    private boolean amILoaded;

    @Shadow
    private boolean couldRun;

    @Shadow
    private boolean throttlingDisabled;

    @Shadow
    private int ticksSinceUpdateChanged;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void skipUpdateWhileControllerUnloaded(CallbackInfo callbackInfo) {
        if (level.isClientSide || level.isLoaded(pos)) {
            return;
        }
        amILoaded = false;
        masucraftfixes$oweGraphRebuild();
        callbackInfo.cancel();
    }

    @Override
    public void masucraftfixes$oweGraphRebuild() {
        couldRun = false;
        ticksSinceUpdateChanged = 0;
        throttlingDisabled = false;
    }
}
