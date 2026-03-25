package com.masuary.masucraftfixes.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.quark.content.tools.entity.Pickarang;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@Mixin(Pickarang.class)
public abstract class PickarangMixin {

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    public void preventTaggedPassengers(@Nonnull Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if (passenger.getTags().contains("fake_item") || passenger.getTags().contains("PreventMagnetMovement")) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            ordinal = 0))
    private List<ItemEntity> filterTaggedItemsFromPickup(Level level, Class<ItemEntity> entityClass, AABB boundingBox) {
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, boundingBox);
        ArrayList<ItemEntity> filtered = new ArrayList<>(entities);
        filtered.removeIf(entity ->
                entity.getTags().contains("fake_item") || entity.getTags().contains("PreventMagnetMovement")
        );
        return filtered;
    }
}
