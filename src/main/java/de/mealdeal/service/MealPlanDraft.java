package de.mealdeal.service;

import de.mealdeal.domain.Recipe;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The locally selected state of one day before a weekly plan is saved.
 *
 * <p>An empty recipe represents an intentionally unplanned day. The draft stays
 * independent from JavaFX so other callers can use the same batch-save use case.</p>
 */
public record MealPlanDraft(LocalDate date, Optional<Recipe> recipe, int servingCount) {

    public MealPlanDraft {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        Objects.requireNonNull(recipe, "Recipe selection must not be null.");
        if (servingCount <= 0) {
            throw new IllegalArgumentException("Serving count must be positive.");
        }
    }

    /** Creates a draft that adds or keeps a recipe for its date. */
    public static MealPlanDraft planned(LocalDate date, Recipe recipe, int servingCount) {
        return new MealPlanDraft(date, Optional.of(Objects.requireNonNull(
                recipe, "Recipe must not be null.")), servingCount);
    }

    /** Creates a draft that leaves its date without a plan. */
    public static MealPlanDraft unplanned(LocalDate date) {
        return new MealPlanDraft(date, Optional.empty(), 1);
    }
}
