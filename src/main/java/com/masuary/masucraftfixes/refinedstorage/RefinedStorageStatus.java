package com.masuary.masucraftfixes.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.apiimpl.API;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Per-dimension numbers for {@code /masucraftfixes status}. Zero networks and nodes means no line. */
public record RefinedStorageStatus(String dimension, int networks, int offlineNetworks, int unreadSavedTasks,
                                   int nodes, int pausedNodes, int pendingReconnects) {

    public static List<String> describeLevels(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            RefinedStorageStatus status = of(level);
            if (!status.isEmpty()) {
                lines.add("  " + status.describe());
            }
        }
        return lines;
    }

    private static RefinedStorageStatus of(ServerLevel level) {
        int networks = 0;
        int offlineNetworks = 0;
        int unreadSavedTasks = 0;
        for (INetwork network : API.instance().getNetworkManager(level).all()) {
            networks++;
            if (!level.isLoaded(network.getPosition())) {
                offlineNetworks++;
            }
            if (network.getCraftingManager() instanceof UnreadCraftingTasks unreadCraftingTasks) {
                unreadSavedTasks += unreadCraftingTasks.masucraftfixes$unreadSavedTaskCount();
            }
        }
        int nodes = 0;
        int pausedNodes = 0;
        for (INetworkNode node : API.instance().getNetworkNodeManager(level).all()) {
            nodes++;
            if (!level.isLoaded(node.getPos())) {
                pausedNodes++;
            }
        }
        return new RefinedStorageStatus(level.dimension().location().toString(), networks, offlineNetworks,
                unreadSavedTasks, nodes, pausedNodes, RefinedStorageReconnects.pendingCount(level));
    }

    private boolean isEmpty() {
        return networks == 0 && nodes == 0;
    }

    private String describe() {
        return "%s: %d networks (%d offline, controller chunk unloaded; %d saved crafting tasks waiting), %d nodes (%d paused), %d reconnects pending"
                .formatted(dimension, networks, offlineNetworks, unreadSavedTasks, nodes, pausedNodes, pendingReconnects);
    }
}
