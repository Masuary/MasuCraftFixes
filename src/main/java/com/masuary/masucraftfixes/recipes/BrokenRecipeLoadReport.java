package com.masuary.masucraftfixes.recipes;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public record BrokenRecipeLoadReport(int skippedCount, SortedSet<String> nowLoading, SortedSet<String> noLongerShipped) {

    public static BrokenRecipeLoadReport of(Set<String> knownBrokenRecipeIds, Set<String> shippedRecipeIds,
                                            Set<String> loadedRecipeIds) {
        int skippedCount = 0;
        SortedSet<String> nowLoading = new TreeSet<>();
        SortedSet<String> noLongerShipped = new TreeSet<>();
        for (String recipeId : knownBrokenRecipeIds) {
            if (loadedRecipeIds.contains(recipeId)) {
                nowLoading.add(recipeId);
            } else if (shippedRecipeIds.contains(recipeId)) {
                skippedCount++;
            } else {
                noLongerShipped.add(recipeId);
            }
        }
        return new BrokenRecipeLoadReport(skippedCount, Collections.unmodifiableSortedSet(nowLoading),
                Collections.unmodifiableSortedSet(noLongerShipped));
    }
}
