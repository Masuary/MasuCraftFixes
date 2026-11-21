package com.masuary.masucraftfixes.refinedstorage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeReconnectTest {

    @Test
    void nodeThatAlreadyHasANetworkAsksForNothing() {
        assertTrue(NodeReconnect.networksToRebuild("own", List.of("neighbour")).isEmpty());
    }

    @Test
    void nodeWithoutNeighbourNetworksAsksForNothing() {
        assertTrue(NodeReconnect.networksToRebuild(null, Arrays.asList(null, null)).isEmpty());
    }

    @Test
    void eachNeighbourNetworkIsRebuiltOnceInNeighbourOrder() {
        Set<String> networks = NodeReconnect.networksToRebuild(null, Arrays.asList("east", null, "west", "east"));

        assertEquals(List.of("east", "west"), List.copyOf(networks));
    }
}
