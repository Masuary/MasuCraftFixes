package com.masuary.masucraftfixes.mixin;

import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.common.block.subtile.functional.SubTileOrechidIgnem;

@Mixin(value = SubTileOrechidIgnem.class, remap = false)
public abstract class OrechidIgnemMixin {

    @Inject(method = "canOperate", at = @At("HEAD"), cancellable = true)
    private void allowOperationInNetherBiome(CallbackInfoReturnable<Boolean> callbackInfo) {
        BlockEntity flowerBlockEntity = (BlockEntity) (Object) this;
        Level level = flowerBlockEntity.getLevel();
        if (level != null
                && !level.isClientSide
                && level.getBiome(flowerBlockEntity.getBlockPos()).is(BiomeTags.IS_NETHER)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
