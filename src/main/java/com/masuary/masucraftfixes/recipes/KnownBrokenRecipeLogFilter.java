package com.masuary.masucraftfixes.recipes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.Set;

/**
 * Steam Rails already redirects RecipeManager's "Parsing error loading recipe" call (priority 10000), so a second
 * redirect cannot apply; filtering the log event leaves that call and its owner untouched.
 */
public final class KnownBrokenRecipeLogFilter extends AbstractFilter {

    public static final String RECIPE_MANAGER_LOGGER = "net.minecraft.world.item.crafting.RecipeManager";
    public static final String PARSING_ERROR_FORMAT = "Parsing error loading recipe {}";

    private final Set<String> knownBrokenRecipeIds;

    public KnownBrokenRecipeLogFilter(Set<String> knownBrokenRecipeIds) {
        super(Result.DENY, Result.NEUTRAL);
        this.knownBrokenRecipeIds = knownBrokenRecipeIds;
    }

    public static void install(Set<String> knownBrokenRecipeIds) {
        if (!(LogManager.getLogger(RECIPE_MANAGER_LOGGER) instanceof Logger recipeManagerLogger)) {
            throw new IllegalStateException("MasuCraftFixes needs log4j-core behind " + RECIPE_MANAGER_LOGGER
                    + " to hide the known broken pack recipes");
        }
        KnownBrokenRecipeLogFilter filter = new KnownBrokenRecipeLogFilter(knownBrokenRecipeIds);
        filter.start();
        recipeManagerLogger.addFilter(filter);
    }

    @Override
    public Result filter(LogEvent event) {
        return isKnownBrokenRecipeError(event.getLoggerName(), event.getMessage()) ? onMatch : onMismatch;
    }

    boolean isKnownBrokenRecipeError(String loggerName, Message message) {
        if (!RECIPE_MANAGER_LOGGER.equals(loggerName) || message == null || !PARSING_ERROR_FORMAT.equals(message.getFormat())) {
            return false;
        }
        Object[] parameters = message.getParameters();
        return parameters != null && parameters.length > 0 && knownBrokenRecipeIds.contains(String.valueOf(parameters[0]));
    }
}
