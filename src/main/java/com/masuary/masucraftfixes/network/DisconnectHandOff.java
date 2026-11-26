package com.masuary.masucraftfixes.network;

import com.masuary.masucraftfixes.MasuCraftFixes;
import net.minecraft.util.thread.BlockableEventLoop;

/**
 * Vanilla's {@code disconnect} waits for the server thread to run {@code handleDisconnection}. From a Vault Hunters
 * vault worker (CONCURRENT vaults tick there while the server thread waits for them) that wait never ends, so an
 * off-thread disconnect only queues the handling instead.
 */
public final class DisconnectHandOff {

    private DisconnectHandOff() {
    }

    public static void handleDisconnection(BlockableEventLoop<?> server, Runnable handleDisconnection, String playerName) {
        if (server.isSameThread()) {
            server.executeBlocking(handleDisconnection);
            return;
        }
        MasuCraftFixes.LOGGER.warn("{} was disconnected from thread {}; their disconnection is handed to the server thread "
                + "instead of waited for (a wait from a vault worker hangs the server)", playerName, Thread.currentThread().getName());
        server.execute(handleDisconnection);
    }
}
