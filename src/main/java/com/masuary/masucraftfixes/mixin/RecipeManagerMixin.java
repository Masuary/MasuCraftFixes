package com.masuary.masucraftfixes.mixin;

import com.google.gson.JsonElement;
import com.masuary.masucraftfixes.MasuCraftFixes;
import com.masuary.masucraftfixes.recipes.BrokenRecipeLoadReport;
import com.masuary.masucraftfixes.recipes.KnownBrokenPackRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void reportKnownBrokenPackRecipes(Map<ResourceLocation, JsonElement> recipeJsons, ResourceManager resourceManager,
                                              ProfilerFiller profiler, CallbackInfo callbackInfo) {
        BrokenRecipeLoadReport report = BrokenRecipeLoadReport.of(KnownBrokenPackRecipes.bundled(),
                recipeIdTexts(recipeJsons.keySet()), recipeIdTexts(byName.keySet()));
        MasuCraftFixes.LOGGER.info("Skipped {} recipes the pack ships broken without their parse errors (list: {});"
                + " other recipe errors still log", report.skippedCount(), KnownBrokenPackRecipes.RESOURCE_PATH);
        if (!report.nowLoading().isEmpty()) {
            MasuCraftFixes.LOGGER.warn("Listed broken pack recipes that load now (remove them from {}): {}",
                    KnownBrokenPackRecipes.RESOURCE_PATH, report.nowLoading());
        }
        if (!report.noLongerShipped().isEmpty()) {
            MasuCraftFixes.LOGGER.warn("Listed broken pack recipes that no mod ships any more (remove them from {}): {}",
                    KnownBrokenPackRecipes.RESOURCE_PATH, report.noLongerShipped());
        }
    }

    private static Set<String> recipeIdTexts(Set<ResourceLocation> recipeIds) {
        return recipeIds.stream().map(ResourceLocation::toString).collect(Collectors.toSet());
    }
}
