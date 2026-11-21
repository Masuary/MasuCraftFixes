package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.LoadTrackedNode;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = NetworkNode.class, remap = false)
public abstract class NetworkNodeMixin implements LoadTrackedNode {

    @Unique
    private boolean masucraftfixes$loadedLastTick;

    @Override
    public boolean masucraftfixes$recordLoaded(boolean loaded) {
        boolean loadedBefore = masucraftfixes$loadedLastTick;
        masucraftfixes$loadedLastTick = loaded;
        return loadedBefore;
    }
}
