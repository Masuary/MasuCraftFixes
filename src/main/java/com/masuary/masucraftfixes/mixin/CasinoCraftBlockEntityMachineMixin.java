package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.CasinoCraftAuditLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mod.casinocraft.blockentity.BlockEntityMachine", remap = false)
public class CasinoCraftBlockEntityMachineMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), require = 0)
    private static void masucraftfixes$beforeServerTick(Level level, BlockPos pos, BlockState state, @Coerce Object blockEntity, CallbackInfo ci) {
        CasinoCraftAuditLogger.beforeMachineTick(level, pos, blockEntity);
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private static void masucraftfixes$afterServerTick(Level level, BlockPos pos, BlockState state, @Coerce Object blockEntity, CallbackInfo ci) {
        CasinoCraftAuditLogger.afterMachineTick(level, pos, blockEntity);
    }
}
