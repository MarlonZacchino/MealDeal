package de.mealdeal.ui.navigation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.service.recommendation.RecommendationSession;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Temporary serving and alternative choices for the existing detail view. */
public record RecipeDetailContext(Recipe recipe, int servings, Map<UUID, UUID> selectedOptions) {

    public RecipeDetailContext {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        if (servings < 1 || servings > 999) {
            throw new IllegalArgumentException("Detail servings must be between 1 and 999.");
        }
        selectedOptions = Map.copyOf(selectedOptions);
        selectedOptions.forEach((groupId, optionId) -> {
            var group = recipe.getIngredientGroups().stream()
                    .filter(value -> value.getId().equals(groupId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient group."));
            if (group.getOptions().stream().noneMatch(value -> value.getId().equals(optionId))) {
                throw new IllegalArgumentException("Ingredient option does not belong to its group.");
            }
        });
    }

    /** Uses the Recipe's defaults when opened outside a recommendation. */
    public static RecipeDetailContext standard(Recipe recipe) {
        return new RecipeDetailContext(recipe, recipe.getStandardServingCount(), Map.of());
    }

    /** Carries the exact choices used for this result's pantry assessment. */
    public static RecipeDetailContext recommended(RecommendationSession session, UUID recipeId) {
        var result = session.recommendationFor(recipeId).orElseThrow();
        return new RecipeDetailContext(result.recipe(), session.requestedServings(),
                result.suggestedIngredientOptions());
    }
}
