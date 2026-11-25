package com.masuary.masucraftfixes.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownBrokenPackRecipesTest {

    @Test
    void bundledListHoldsEveryBrokenRecipeOfThePack() {
        Set<String> recipeIds = KnownBrokenPackRecipes.bundled();

        assertEquals(105, recipeIds.size());
        assertTrue(recipeIds.contains("create:cutting/compat/regions_unexplored/magnolia_wood"));
        assertTrue(recipeIds.contains("irongenerators:skystone_generator_rcp"));
        assertTrue(recipeIds.contains("pneumaticcraft:pneumaticcraft_fuels/cofh_biofuel"));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() {
        Set<String> recipeIds = KnownBrokenPackRecipes.parse(List.of("# heading", "", "  twigs:misc/bone_meal_from_sea_shell  "));

        assertEquals(Set.of("twigs:misc/bone_meal_from_sea_shell"), recipeIds);
    }

    @Test
    void lineWithoutNamespaceFailsWithItsLineNumber() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> KnownBrokenPackRecipes.parse(List.of("# heading", "bone_meal_from_sea_shell")));

        assertTrue(failure.getMessage().contains("line 2"));
    }

    @Test
    void repeatedRecipeIdFails() {
        assertThrows(IllegalStateException.class,
                () -> KnownBrokenPackRecipes.parse(List.of("twigs:misc/bone_meal_from_sea_shell", "twigs:misc/bone_meal_from_sea_shell")));
    }

    @Test
    void listWithoutRecipeIdsFails() {
        assertThrows(IllegalStateException.class, () -> KnownBrokenPackRecipes.parse(List.of("# only a heading")));
    }
}
