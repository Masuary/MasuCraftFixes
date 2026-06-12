package com.masuary.masucraftfixes;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;

public class Stage0TelemetryEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onItemPickup(EntityItemPickupEvent event) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemEntity itemEntity = event.getItem();
        Stage0Telemetry.recordPickup(
                player,
                itemEntity,
                vaultId(player.getLevel()),
                event.isCanceled(),
                itemEntity != null && itemEntity.isRemoved()
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        LevelAccessor accessor = event.getWorld();
        if (!(accessor instanceof Level level)) {
            return;
        }

        Stage0Telemetry.recordBlockBreak(player, level, vaultId(level), event.isCanceled(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Stage0Telemetry.onServerTick(server);
    }

    private static String vaultId(Level level) {
        if (!(level instanceof ServerLevel) || level.isClientSide) {
            return null;
        }

        ResourceLocation dimension = level.dimension().location();
        if (!"the_vault".equals(dimension.getNamespace())) {
            return null;
        }

        Optional<Vault> vault = ServerVaults.get(level);
        if (vault.isPresent() && vault.get().has(Vault.ID)) {
            return vault.get().get(Vault.ID).toString();
        }
        return null;
    }
}
