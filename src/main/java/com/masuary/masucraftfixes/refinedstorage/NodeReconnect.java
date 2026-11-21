package com.masuary.masucraftfixes.refinedstorage;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Refined Storage builds a network's graph only from nodes whose chunks are loaded at that moment, so a node whose
 * chunk loads later stays outside its network until a cable next to it changes. A node that loads without a network
 * next to a node that has one asks that network to rebuild.
 */
public final class NodeReconnect {

    private NodeReconnect() {
    }

    public static <NETWORK> Set<NETWORK> networksToRebuild(@Nullable NETWORK ownNetwork, Iterable<NETWORK> neighbourNetworks) {
        if (ownNetwork != null) {
            return Collections.emptySet();
        }
        Set<NETWORK> networks = new LinkedHashSet<>();
        for (NETWORK neighbourNetwork : neighbourNetworks) {
            if (neighbourNetwork != null) {
                networks.add(neighbourNetwork);
            }
        }
        return networks;
    }
}
