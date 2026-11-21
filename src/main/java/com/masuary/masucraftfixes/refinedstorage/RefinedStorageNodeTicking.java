package com.masuary.masucraftfixes.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeManager;
import com.refinedmods.refinedstorage.apiimpl.API;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Refined Storage ticks every node of a dimension each tick, loaded or not, and several node types (and add-on nodes
 * such as Vault Integrations' Altar Conduit) read their own block or block entity, which loads the chunk again. Nodes
 * in unloaded chunks are skipped as a whole, so they pause exactly where they were.
 */
public final class RefinedStorageNodeTicking {

    private RefinedStorageNodeTicking() {
    }

    public static void tickIfLoaded(INetworkNode node) {
        ServerLevel level = (ServerLevel) node.getLevel();
        boolean loaded = level.isLoaded(node.getPos());
        if (node instanceof LoadTrackedNode loadTrackedNode && !loadTrackedNode.masucraftfixes$recordLoaded(loaded) && loaded) {
            reconnectToNeighbourNetworks(level, node);
        }
        if (loaded) {
            node.update();
        }
    }

    private static void reconnectToNeighbourNetworks(ServerLevel level, INetworkNode node) {
        INetworkNodeManager nodeManager = API.instance().getNetworkNodeManager(level);
        List<INetwork> neighbourNetworks = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPosition = node.getPos().relative(direction);
            INetworkNode neighbour = nodeManager.getNode(neighbourPosition);
            if (neighbour != null) {
                neighbourNetworks.add(neighbour.getNetwork());
            }
        }
        for (INetwork network : NodeReconnect.networksToRebuild(node.getNetwork(), neighbourNetworks)) {
            RefinedStorageReconnects.schedule(level, network);
        }
    }
}
