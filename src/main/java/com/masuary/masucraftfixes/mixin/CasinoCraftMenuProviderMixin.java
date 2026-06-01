package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.CasinoCraftAuditLogger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mod.casinocraft.menu.MenuProvider", remap = false)
public class CasinoCraftMenuProviderMixin {
    @Inject(method = "m_7208_", at = @At("RETURN"), require = 0)
    private void masucraftfixes$logAdminAccess(int windowId, Inventory playerInventory, Player playerEntity, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        CasinoCraftAuditLogger.logAdminAccess(playerEntity, cir.getReturnValue(), this);
    }
}
