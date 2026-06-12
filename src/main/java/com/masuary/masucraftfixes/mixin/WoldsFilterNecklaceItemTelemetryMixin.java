package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.Stage0Telemetry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xyz.iwolfking.woldsvaults.items.filter_necklace.FilterNecklaceItem", remap = false)
public abstract class WoldsFilterNecklaceItemTelemetryMixin {
    @Inject(method = "stackMatchesFilter", at = @At("HEAD"), require = 0, remap = false)
    private void masucraftfixes$stage0StartStackMatchesFilter(ItemStack stack, ItemStack necklace, CallbackInfoReturnable<Boolean> cir) {
        Stage0Telemetry.startWoldsStackMatchesFilter();
    }

    @Inject(method = "stackMatchesFilter", at = @At("RETURN"), require = 0, remap = false)
    private void masucraftfixes$stage0FinishStackMatchesFilter(ItemStack stack, ItemStack necklace, CallbackInfoReturnable<Boolean> cir) {
        Stage0Telemetry.finishWoldsStackMatchesFilter(stack, necklace, Boolean.TRUE.equals(cir.getReturnValue()));
    }

    @Inject(method = "getInventory", at = @At("HEAD"), require = 0, remap = false)
    private void masucraftfixes$stage0StartGetInventory(ItemStack stack, CallbackInfoReturnable<IItemHandler> cir) {
        Stage0Telemetry.startWoldsGetInventory();
    }

    @Inject(method = "getInventory", at = @At("RETURN"), require = 0, remap = false)
    private void masucraftfixes$stage0FinishGetInventory(ItemStack stack, CallbackInfoReturnable<IItemHandler> cir) {
        Stage0Telemetry.finishWoldsGetInventory();
    }
}
