package com.masuary.masucraftfixes.vault;

import com.masuary.masucraftfixes.MasuCraftFixes;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import iskallia.vault.core.data.key.VersionedKey;
import iskallia.vault.core.vault.VaultRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Optional;

/** Adds room pool suggestions to {@code /the_vault modify crystal addRoom <pool>}, which VH registers without any. */
public final class CrystalCommandSuggestions {

    private static final Optional<Field> CUSTOM_SUGGESTIONS_FIELD = resolveCustomSuggestionsField();

    private CrystalCommandSuggestions() {
    }

    public static boolean isAvailable() {
        return CUSTOM_SUGGESTIONS_FIELD.isPresent();
    }

    public static void suggestRoomPools(ArgumentCommandNode<CommandSourceStack, ?> poolArgument) {
        if (CUSTOM_SUGGESTIONS_FIELD.isEmpty()) {
            return;
        }
        SuggestionProvider<CommandSourceStack> roomPools = (context, builder) -> SharedSuggestionProvider.suggest(
                VaultRegistry.TEMPLATE_POOL.getKeys().stream()
                        .map(VersionedKey::getId)
                        .map(ResourceLocation::toString)
                        .toList(),
                builder);
        try {
            CUSTOM_SUGGESTIONS_FIELD.get().set(poolArgument, roomPools);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Brigadier ArgumentCommandNode.customSuggestions is not writable", exception);
        }
    }

    private static Optional<Field> resolveCustomSuggestionsField() {
        try {
            Field field = ArgumentCommandNode.class.getDeclaredField("customSuggestions");
            field.setAccessible(true);
            return Optional.of(field);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MasuCraftFixes.LOGGER.error("Room pool suggestions for /the_vault modify crystal addRoom are off: "
                    + "Brigadier ArgumentCommandNode.customSuggestions could not be opened", exception);
            return Optional.empty();
        }
    }
}
