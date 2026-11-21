package com.masuary.masucraftfixes.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph rebuilds asked for by nodes that loaded after their network was built. A rebuild waits until no further
 * node of that network has loaded for {@link #SETTLE_TICKS} ticks, so a base that loads chunk by chunk rebuilds once.
 */
public final class RefinedStorageReconnects {

    private static final long SETTLE_TICKS = 20;
    private static final Map<ResourceKey<Level>, DueQueue<INetwork>> QUEUE_BY_LEVEL = new HashMap<>();

    private RefinedStorageReconnects() {
    }

    /** Keyed by the network's own level: a node behind a Network Receiver can sit in another dimension. */
    public static void schedule(INetwork network) {
        Level networkLevel = network.getLevel();
        QUEUE_BY_LEVEL.computeIfAbsent(networkLevel.dimension(), dimension -> new DueQueue<>())
                .schedule(network, networkLevel.getGameTime() + SETTLE_TICKS);
    }

    public static void rebuildDueNetworks(ServerLevel level) {
        DueQueue<INetwork> queue = QUEUE_BY_LEVEL.get(level.dimension());
        if (queue == null || queue.size() == 0) {
            return;
        }
        for (INetwork network : queue.takeDue(level.getGameTime())) {
            rebuildIfStillPresent(level, network);
        }
    }

    public static int pendingCount(ServerLevel level) {
        DueQueue<INetwork> queue = QUEUE_BY_LEVEL.get(level.dimension());
        return queue == null ? 0 : queue.size();
    }

    /** A network whose controller chunk is unloaded already owes a rebuild for when it loads again. */
    private static void rebuildIfStillPresent(ServerLevel level, INetwork network) {
        boolean stillRegistered = API.instance().getNetworkManager(level).getNetwork(network.getPosition()) == network;
        if (stillRegistered && level.isLoaded(network.getPosition())) {
            network.getNodeGraph().invalidate(Action.PERFORM, level, network.getPosition());
        }
    }

    public static class ServerStopListener {

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            QUEUE_BY_LEVEL.clear();
        }
    }
}
