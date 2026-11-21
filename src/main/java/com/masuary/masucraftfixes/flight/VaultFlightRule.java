package com.masuary.masucraftfixes.flight;

import com.masuary.masucraftfixes.vault.VaultDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FTB {@code /fly} is switched off inside vaults (ops keep it). Runs from the player tick event, which the server
 * fires on its main thread for every world; Vault Hunters ticks vault worlds, and the entities in them, on its own
 * worker threads, and FTB Essentials' player data is not thread-safe.
 */
public class VaultFlightRule {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.hasDisconnected() || player.isDeadOrDying() || player.hasPermissions(4)) {
            return;
        }
        if (VaultDimensions.isVaultWorld(player.level)) {
            FtbFlight.disable(player);
        }
    }
}
