package com.masuary.masucraftfixes.mixin;

import net.blay09.mods.craftingtweaks.network.CompressMessage;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CompressMessage.class, remap = false)
public abstract class CompressMessageMixin {

    private static final ThreadLocal<Boolean> insufficientInputs =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Redirect(
        method = "compressMouseSlot",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"),
        remap = true)
    private static void masucraftfixes$validateBeforeShrink(ItemStack mouseStack, int amount) {
        if (mouseStack.getCount() < amount) {
            insufficientInputs.set(Boolean.TRUE);
            return;
        }
        insufficientInputs.set(Boolean.FALSE);
        mouseStack.shrink(amount);
    }

    @ModifyArg(
        method = "compressMouseSlot",
        at = @At(value = "INVOKE",
                 target = "Lnet/blay09/mods/craftingtweaks/network/CompressMessage;"
                        + "addCraftedItemsToInventory(Lnet/minecraft/world/entity/player/Player;"
                        + "Lnet/minecraft/world/item/ItemStack;I)V"),
        index = 2)
    private static int masucraftfixes$zeroCraftsWhenInsufficient(int timesCrafted) {
        if (insufficientInputs.get()) {
            insufficientInputs.set(Boolean.FALSE);
            return 0;
        }
        return timesCrafted;
    }
}
