package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.permissions.LuckPermsAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandBlockEditMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCommandBlock", at = @At("HEAD"), cancellable = true)
    private void blockCommandBlockEditWithoutPermission(ServerboundSetCommandBlockPacket packet, CallbackInfo callbackInfo) {
        masucraftfixes$denyCommandBlockEditWithoutPermission(callbackInfo);
    }

    @Inject(method = "handleSetCommandMinecart", at = @At("HEAD"), cancellable = true)
    private void blockCommandMinecartEditWithoutPermission(ServerboundSetCommandMinecartPacket packet, CallbackInfo callbackInfo) {
        masucraftfixes$denyCommandBlockEditWithoutPermission(callbackInfo);
    }

    @Unique
    private void masucraftfixes$denyCommandBlockEditWithoutPermission(CallbackInfo callbackInfo) {
        if (!ModList.get().isLoaded("luckperms") || LuckPermsAccess.canEditCommandBlocks(player)) {
            return;
        }
        player.sendMessage(new TextComponent("You don't have permission to edit command blocks.")
                .withStyle(ChatFormatting.RED), player.getUUID());
        callbackInfo.cancel();
    }
}
