package com.masuary.masucraftfixes.recipes;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokenRecipeLoadReportTest {

    private static final Set<String> KNOWN_BROKEN = Set.of("create:a", "create:b", "twigs:c");

    @Test
    void everyListedRecipeShippedAndNotLoadedCountsAsSkipped() {
        BrokenRecipeLoadReport report = BrokenRecipeLoadReport.of(KNOWN_BROKEN,
                Set.of("create:a", "create:b", "twigs:c", "create:other"), Set.of("create:other"));

        assertEquals(3, report.skippedCount());
        assertTrue(report.nowLoading().isEmpty());
        assertTrue(report.noLongerShipped().isEmpty());
    }

    @Test
    void listedRecipeThatLoadedIsReportedAsNowLoading() {
        BrokenRecipeLoadReport report = BrokenRecipeLoadReport.of(KNOWN_BROKEN,
                Set.of("create:a", "create:b", "twigs:c"), Set.of("create:b"));

        assertEquals(2, report.skippedCount());
        assertEquals(Set.of("create:b"), report.nowLoading());
        assertTrue(report.noLongerShipped().isEmpty());
    }

    @Test
    void listedRecipeMissingFromTheDataIsReportedAsNoLongerShipped() {
        BrokenRecipeLoadReport report = BrokenRecipeLoadReport.of(KNOWN_BROKEN, Set.of("create:a", "create:b"), Set.of());

        assertEquals(2, report.skippedCount());
        assertTrue(report.nowLoading().isEmpty());
        assertEquals(Set.of("twigs:c"), report.noLongerShipped());
    }
}
