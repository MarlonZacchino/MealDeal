package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Describes why and how strongly a recipe matched selected ingredients. */
public final class IngredientSearchResult {

    private final Recipe recipe;
    private final List<RecipeIngredientGroup> matchedGroups;
    private final List<RecipeIngredientGroup> missingGroups;
    private final List<Ingredient> matchedIngredients;
    private final List<Ingredient> missingIngredients;
    private final int selectedCount;
    private final BigDecimal matchRatio;
    private final MatchQuality matchQuality;

    IngredientSearchResult(Recipe recipe,
                           List<RecipeIngredientGroup> matchedGroups,
                           List<RecipeIngredientGroup> missingGroups,
                           List<Ingredient> matchedIngredients,
                           BigDecimal matchRatio) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        this.matchedGroups = List.copyOf(matchedGroups);
        this.missingGroups = List.copyOf(missingGroups);
        this.matchedIngredients = List.copyOf(matchedIngredients);
        this.missingIngredients = this.missingGroups.stream()
                .map(group -> group.getStandardOption().getIngredient())
                .toList();
        this.selectedCount = this.matchedGroups.size() + this.missingGroups.size();
        this.matchRatio = Objects.requireNonNull(matchRatio, "Match ratio must not be null.");
        this.matchQuality = MatchQuality.fromCounts(getMatchedCount(), selectedCount);
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getMatchedCount() {
        return matchedGroups.size();
    }

    /**
     * Returns the recipe's required ingredient-group count.
     * The established accessor name remains for UI and API compatibility.
     */
    public int getSelectedCount() {
        return selectedCount;
    }

    public BigDecimal getMatchRatio() {
        return matchRatio;
    }

    public MatchQuality getMatchQuality() {
        return matchQuality;
    }

    public List<Ingredient> getMatchedIngredients() {
        return matchedIngredients;
    }

    public List<Ingredient> getMissingIngredients() {
        return missingIngredients;
    }

    /** Returns the recipe groups satisfied by at least one selected option. */
    public List<RecipeIngredientGroup> getMatchedGroups() {
        return matchedGroups;
    }

    /** Returns unsatisfied groups in their original recipe order. */
    public List<RecipeIngredientGroup> getMissingGroups() {
        return missingGroups;
    }
}
