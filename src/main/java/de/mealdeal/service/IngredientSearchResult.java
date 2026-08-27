package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Describes why and how strongly a recipe matched selected ingredients. */
public final class IngredientSearchResult {

    private final Recipe recipe;
    private final List<Ingredient> matchedIngredients;
    private final List<Ingredient> missingIngredients;
    private final int selectedCount;
    private final BigDecimal matchRatio;
    private final MatchQuality matchQuality;

    IngredientSearchResult(Recipe recipe, List<Ingredient> matchedIngredients,
                           List<Ingredient> missingIngredients, BigDecimal matchRatio) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        this.matchedIngredients = List.copyOf(matchedIngredients);
        this.missingIngredients = List.copyOf(missingIngredients);
        this.selectedCount = this.matchedIngredients.size() + this.missingIngredients.size();
        this.matchRatio = Objects.requireNonNull(matchRatio, "Match ratio must not be null.");
        this.matchQuality = MatchQuality.fromCounts(getMatchedCount(), selectedCount);
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getMatchedCount() {
        return matchedIngredients.size();
    }

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
}
