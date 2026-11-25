package com.masuary.masucraftfixes.recipes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class KnownBrokenPackRecipes {

    public static final String RESOURCE_PATH = "/masucraftfixes/known_broken_pack_recipes.txt";

    private static final Pattern RECIPE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static Set<String> bundledRecipeIds;

    private KnownBrokenPackRecipes() {
    }

    public static synchronized Set<String> bundled() {
        if (bundledRecipeIds == null) {
            bundledRecipeIds = parse(readBundledLines());
        }
        return bundledRecipeIds;
    }

    public static Set<String> parse(List<String> lines) {
        Set<String> recipeIds = new LinkedHashSet<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = lineIndex + 1;
            if (!RECIPE_ID.matcher(line).matches()) {
                throw new IllegalStateException(RESOURCE_PATH + " line " + lineNumber + " is not a recipe id: " + line);
            }
            if (!recipeIds.add(line)) {
                throw new IllegalStateException(RESOURCE_PATH + " line " + lineNumber + " repeats " + line);
            }
        }
        if (recipeIds.isEmpty()) {
            throw new IllegalStateException(RESOURCE_PATH + " lists no recipe ids");
        }
        return Set.copyOf(recipeIds);
    }

    private static List<String> readBundledLines() {
        InputStream stream = KnownBrokenPackRecipes.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("MasuCraftFixes jar is missing " + RESOURCE_PATH);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + RESOURCE_PATH, exception);
        }
    }
}
