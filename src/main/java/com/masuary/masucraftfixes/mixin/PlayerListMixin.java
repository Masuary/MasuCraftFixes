package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.AbandonedVaults;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @ModifyVariable(method = "placeNewPlayer", at = @At("STORE"), ordinal = 0)
    private ResourceKey<Level> leaveDeadVaultWorld(ResourceKey<Level> dimension, Connection connection, ServerPlayer player) {
        if (!ModList.get().isLoaded("the_vault")) {
            return dimension;
        }
        return AbandonedVaults.rescueFromDeadVault(server, player, dimension);
    }
}
