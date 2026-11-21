package com.masuary.masucraftfixes.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;

public final class UnloadedNetworks {

    private UnloadedNetworks() {
    }

    /** A network whose controller chunk is unloaded is offline and does not run (see {@code NetworkMixin}). */
    public static boolean isControllerUnloaded(INetwork network) {
        return !network.getLevel().isLoaded(network.getPosition());
    }
}
