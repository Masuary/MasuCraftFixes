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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Graph rebuilds asked for by nodes that loaded after their network was built. A rebuild waits until no further
 * node of that network has loaded for {@link #SETTLE_TICKS} ticks, so a base that loads chunk by chunk rebuilds once.
 */
public final class RefinedStorageReconnects {

    private static final long SETTLE_TICKS = 20;
    private static final Map<ResourceKey<Level>, Map<INetwork, Long>> DUE_GAME_TIME_BY_LEVEL = new HashMap<>();

    private RefinedStorageReconnects() {
    }

    public static void schedule(ServerLevel level, INetwork network) {
        DUE_GAME_TIME_BY_LEVEL.computeIfAbsent(level.dimension(), dimension -> new IdentityHashMap<>())
                .put(network, level.getGameTime() + SETTLE_TICKS);
    }

    public static void rebuildDueNetworks(ServerLevel level) {
        Map<INetwork, Long> dueGameTimes = DUE_GAME_TIME_BY_LEVEL.get(level.dimension());
        if (dueGameTimes == null || dueGameTimes.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        Iterator<Map.Entry<INetwork, Long>> entries = dueGameTimes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<INetwork, Long> entry = entries.next();
            if (entry.getValue() > gameTime) {
                continue;
            }
            entries.remove();
            rebuildIfStillPresent(level, entry.getKey());
        }
    }

    public static int pendingCount(ServerLevel level) {
        Map<INetwork, Long> dueGameTimes = DUE_GAME_TIME_BY_LEVEL.get(level.dimension());
        return dueGameTimes == null ? 0 : dueGameTimes.size();
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
            DUE_GAME_TIME_BY_LEVEL.clear();
        }
    }
}
