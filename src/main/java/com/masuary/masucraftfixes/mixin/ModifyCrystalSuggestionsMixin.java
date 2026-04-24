package com.masuary.masucraftfixes.mixin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import iskallia.vault.command.modify.ModifyCrystalSubcommand;
import iskallia.vault.core.data.key.VersionedKey;
import iskallia.vault.core.vault.VaultRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.stream.Collectors;

@Mixin(value = ModifyCrystalSubcommand.class, remap = false)
public class ModifyCrystalSuggestionsMixin {

    @Inject(method = "register", at = @At("TAIL"))
    private static void addRoomPoolSuggestions(LiteralArgumentBuilder<CommandSourceStack> builder, CallbackInfo ci) {
        for (CommandNode<CommandSourceStack> node : builder.getArguments()) {
            if (!node.getName().equals("addRoom")) continue;

            CommandNode<CommandSourceStack> poolNode = node.getChild("pool");
            if (!(poolNode instanceof ArgumentCommandNode<?, ?> argumentNode)) break;

            SuggestionProvider<CommandSourceStack> suggestPool = (context, suggestionsBuilder) ->
                SharedSuggestionProvider.suggest(
                    VaultRegistry.TEMPLATE_POOL.getKeys().stream()
                        .map(VersionedKey::getId)
                        .map(ResourceLocation::toString)
                        .collect(Collectors.toList()),
                    suggestionsBuilder
                );

            try {
                Field customSuggestionsField = ArgumentCommandNode.class.getDeclaredField("customSuggestions");
                customSuggestionsField.setAccessible(true);
                customSuggestionsField.set(argumentNode, suggestPool);
            } catch (ReflectiveOperationException ignored) {
            }
            break;
        }
    }
}
