package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.FtbEssentialsFlightCompatibility;
import dev.ftb.mods.ftbessentials.command.CheatCommands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CheatCommands.class, remap = false)
public class FTBCheatCommandsMixin {

    @Inject(method = "fly", at = @At("HEAD"), cancellable = true)
    private static void blockFlyInVault(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (player.hasPermissions(4)) return;

        if (FtbEssentialsFlightCompatibility.isVaultDimension(player)) {
            player.displayClientMessage(new TextComponent("Flight in the Vaults is not allowed!"), true);
            cir.setReturnValue(1);
        }
    }
}
