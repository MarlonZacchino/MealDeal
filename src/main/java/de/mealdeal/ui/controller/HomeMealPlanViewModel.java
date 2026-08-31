package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.service.MealPlanDay;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JavaFX-independent presentation state for one day on the start page.
 *
 * <p>It consumes the already validated {@link MealPlanDay} model, so the
 * start page cannot accidentally treat a side-only day as empty.</p>
 */
public record HomeMealPlanViewModel(Optional<RecipeEntry> mainEntry,
                                    List<RecipeEntry> sideEntries) {

    public HomeMealPlanViewModel {
        mainEntry = Objects.requireNonNull(mainEntry, "Main entry must not be null.");
        sideEntries = List.copyOf(Objects.requireNonNull(sideEntries,
                "Side entries must not be null."));
    }

    /** Builds display state without changing the MAIN/SIDE order supplied by the service. */
    public static HomeMealPlanViewModel from(MealPlanDay day) {
        Objects.requireNonNull(day, "Meal plan day must not be null.");
        return new HomeMealPlanViewModel(day.mainEntry().map(RecipeEntry::from),
                day.sideEntries().stream().map(RecipeEntry::from).toList());
    }

    /** Returns whether neither a main dish nor a side dish is planned. */
    public boolean isEmpty() {
        return mainEntry.isEmpty() && sideEntries.isEmpty();
    }

    /** One recipe and its independently planned serving count. */
    public record RecipeEntry(String recipeName, int servingCount) {

        public RecipeEntry {
            if (recipeName == null || recipeName.isBlank()) {
                throw new IllegalArgumentException("Recipe name must not be blank.");
            }
            if (servingCount <= 0) {
                throw new IllegalArgumentException("Serving count must be greater than zero.");
            }
        }

        private static RecipeEntry from(MealPlanEntry entry) {
            Objects.requireNonNull(entry, "Meal plan entry must not be null.");
            return new RecipeEntry(entry.getRecipe().getName(), entry.getServingCount());
        }
    }
}
