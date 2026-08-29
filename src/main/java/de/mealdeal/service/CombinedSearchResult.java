package de.mealdeal.service;

import de.mealdeal.domain.Recipe;

import java.util.Objects;
import java.util.Optional;

/** Combines the unchanged result details of the active recipe-search filters. */
public final class CombinedSearchResult {

    private final Recipe recipe;
    private final IngredientSearchResult ingredientResult;
    private final TasteSearchResult tasteResult;

    private CombinedSearchResult(IngredientSearchResult ingredientResult,
                                 TasteSearchResult tasteResult) {
        if (ingredientResult == null && tasteResult == null) {
            throw new IllegalArgumentException("At least one search result must be present.");
        }
        Recipe resolvedRecipe = ingredientResult == null
                ? tasteResult.getRecipe() : ingredientResult.getRecipe();
        if (tasteResult != null
                && !resolvedRecipe.getId().equals(tasteResult.getRecipe().getId())) {
            throw new IllegalArgumentException("Combined results must refer to the same recipe.");
        }
        recipe = resolvedRecipe;
        this.ingredientResult = ingredientResult;
        this.tasteResult = tasteResult;
    }

    static CombinedSearchResult ingredientsOnly(IngredientSearchResult result) {
        return new CombinedSearchResult(Objects.requireNonNull(result), null);
    }

    static CombinedSearchResult tastesOnly(TasteSearchResult result) {
        return new CombinedSearchResult(null, Objects.requireNonNull(result));
    }

    static CombinedSearchResult combined(IngredientSearchResult ingredientResult,
                                         TasteSearchResult tasteResult) {
        return new CombinedSearchResult(
                Objects.requireNonNull(ingredientResult), Objects.requireNonNull(tasteResult));
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Optional<IngredientSearchResult> getIngredientResult() {
        return Optional.ofNullable(ingredientResult);
    }

    public Optional<TasteSearchResult> getTasteResult() {
        return Optional.ofNullable(tasteResult);
    }

    IngredientSearchResult ingredientResult() {
        return ingredientResult;
    }

    TasteSearchResult tasteResult() {
        return tasteResult;
    }
}
