package com.masuary.masucraftfixes.refinedstorage;

import com.refinedmods.refinedstorage.api.network.node.INetworkNode;

/** Implemented on Refined Storage's {@code NetworkNodeGraph} by {@code NetworkNodeGraphMixin}. */
public interface NodeGraphAccess {

    /** Drops the node from this graph's entries without notifying it, so a later rebuild does not disconnect it. */
    void masucraftfixes$forgetNode(INetworkNode node);
}
