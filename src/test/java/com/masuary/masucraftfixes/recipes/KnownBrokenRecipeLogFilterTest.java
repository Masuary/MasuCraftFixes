package com.masuary.masucraftfixes.recipes;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownBrokenRecipeLogFilterTest {

    private static final String LISTED_RECIPE = "twigs:misc/bone_meal_from_sea_shell";

    private final KnownBrokenRecipeLogFilter filter = new KnownBrokenRecipeLogFilter(Set.of(LISTED_RECIPE));

    @Test
    void parsingErrorOfListedRecipeIsHidden() {
        assertTrue(filter.isKnownBrokenRecipeError(KnownBrokenRecipeLogFilter.RECIPE_MANAGER_LOGGER, parsingError(LISTED_RECIPE)));
    }

    @Test
    void parsingErrorOfUnlistedRecipeStillLogs() {
        assertFalse(filter.isKnownBrokenRecipeError(KnownBrokenRecipeLogFilter.RECIPE_MANAGER_LOGGER,
                parsingError("create:pressing/iron_ingot")));
    }

    @Test
    void otherRecipeManagerMessageAboutListedRecipeStillLogs() {
        assertFalse(filter.isKnownBrokenRecipeError(KnownBrokenRecipeLogFilter.RECIPE_MANAGER_LOGGER,
                new ParameterizedMessage("Skipping loading recipe {} as it's serializer returned null", LISTED_RECIPE)));
    }

    @Test
    void sameMessageFromAnotherLoggerStillLogs() {
        assertFalse(filter.isKnownBrokenRecipeError("com.example.OtherRecipeLoader", parsingError(LISTED_RECIPE)));
    }

    private static ParameterizedMessage parsingError(String recipeId) {
        return new ParameterizedMessage(KnownBrokenRecipeLogFilter.PARSING_ERROR_FORMAT, recipeId,
                new IllegalArgumentException("Unknown item"));
    }
}
