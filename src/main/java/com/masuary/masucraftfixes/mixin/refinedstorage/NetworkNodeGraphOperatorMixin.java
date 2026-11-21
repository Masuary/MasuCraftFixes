package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.NodeGraphAccess;
import com.masuary.masucraftfixes.refinedstorage.UnloadedNetworks;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.util.Action;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A graph rebuild that meets a node of another network breaks that block into items. Nodes of a network whose
 * controller chunk is unloaded still point at it because its rebuilds wait (see {@code NetworkNodeGraphMixin}), so a
 * part cut off from such a network and given its own controller would be broken up. Those nodes are taken over
 * instead, as stock RS would have freed them when the cut happened. The rebuilding network's own nodes never qualify:
 * a rebuild only runs while its controller chunk is loaded.
 */
@Mixin(targets = "com.refinedmods.refinedstorage.apiimpl.network.NetworkNodeGraph$Operator", remap = false)
public abstract class NetworkNodeGraphOperatorMixin {

    @Shadow
    @Final
    private Action action;

    @Redirect(method = "apply", at = @At(value = "INVOKE",
            target = "Lcom/refinedmods/refinedstorage/api/network/node/INetworkNode;getNetwork()Lcom/refinedmods/refinedstorage/api/network/INetwork;"))
    private INetwork adoptNodesOfUnloadedNetworks(INetworkNode node) {
        INetwork currentNetwork = node.getNetwork();
        if (action != Action.PERFORM || currentNetwork == null || !UnloadedNetworks.isControllerUnloaded(currentNetwork)) {
            return currentNetwork;
        }
        if (currentNetwork.getNodeGraph() instanceof NodeGraphAccess previousGraph) {
            previousGraph.masucraftfixes$forgetNode(node);
        }
        node.onDisconnected(currentNetwork);
        return null;
    }
}
