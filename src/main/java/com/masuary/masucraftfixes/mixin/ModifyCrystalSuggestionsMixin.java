package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.vault.CrystalCommandSuggestions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import iskallia.vault.command.modify.ModifyCrystalSubcommand;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModifyCrystalSubcommand.class, remap = false)
public class ModifyCrystalSuggestionsMixin {

    @Inject(method = "register", at = @At("TAIL"))
    private static void addRoomPoolSuggestions(LiteralArgumentBuilder<CommandSourceStack> builder, CallbackInfo callbackInfo) {
        for (CommandNode<CommandSourceStack> node : builder.getArguments()) {
            if (node.getName().equals("addRoom")
                    && node.getChild("pool") instanceof ArgumentCommandNode<CommandSourceStack, ?> poolArgument) {
                CrystalCommandSuggestions.suggestRoomPools(poolArgument);
                return;
            }
        }
    }
}
