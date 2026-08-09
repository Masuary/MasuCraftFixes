package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.FtbEssentialsFlightCompatibility;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void disableFlightInVault(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        if (player.hasDisconnected()) return;
        if (player.isDeadOrDying()) return;
        if (player.hasPermissions(4)) return;

        if (!FtbEssentialsFlightCompatibility.isVaultDimension(player)) return;

        FTBEPlayerData data = FTBEPlayerData.get(player);
        Abilities abilities = player.getAbilities();

        if (data != null && data.fly) {
            data.fly = false;
            data.save();
            abilities.mayfly = false;
            abilities.flying = false;
            player.displayClientMessage(new TextComponent("Flight disabled"), true);
            player.onUpdateAbilities();
        }
    }
}
