package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Combines existing ingredient and taste search results without defining new scores. */
public final class CombinedRecipeSearchService {

    private static final Comparator<Recipe> RECIPE_ORDER = Comparator
            .comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Recipe::getName)
            .thenComparing(Recipe::getId);

    private final RecipeSearchService recipeSearchService;

    public CombinedRecipeSearchService(RecipeSearchService recipeSearchService) {
        this.recipeSearchService = Objects.requireNonNull(
                recipeSearchService, "Recipe search service must not be null.");
    }

    /**
     * Applies each active filter through RecipeSearchService and intersects both result sets.
     * Ingredient ranking remains primary; taste ranking only resolves equal ingredient matches.
     */
    public List<CombinedSearchResult> search(
            Collection<Recipe> recipes,
            Collection<Ingredient> selectedIngredients,
            Collection<Taste> selectedTastes,
            TasteFilterMode tasteMode) {
        Objects.requireNonNull(recipes, "Recipes must not be null.");
        Objects.requireNonNull(selectedIngredients, "Selected ingredients must not be null.");
        Objects.requireNonNull(selectedTastes, "Selected tastes must not be null.");
        Objects.requireNonNull(tasteMode, "Taste filter mode must not be null.");
        if (selectedIngredients.isEmpty() && selectedTastes.isEmpty()) {
            throw new IllegalArgumentException("At least one search filter must be active.");
        }
        if (recipes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipes must not contain null values.");
        }

        List<Recipe> recipeSnapshot = List.copyOf(recipes);
        if (selectedTastes.isEmpty()) {
            return recipeSearchService.searchByIngredients(recipeSnapshot, selectedIngredients)
                    .stream().map(CombinedSearchResult::ingredientsOnly).toList();
        }
        if (selectedIngredients.isEmpty()) {
            return recipeSearchService.searchByTastes(
                            recipeSnapshot, selectedTastes, tasteMode)
                    .stream().map(CombinedSearchResult::tastesOnly).toList();
        }

        List<IngredientSearchResult> ingredientResults = recipeSearchService
                .searchByIngredients(recipeSnapshot, selectedIngredients);
        List<TasteSearchResult> tasteResults = recipeSearchService
                .searchByTastes(recipeSnapshot, selectedTastes, tasteMode);
        Map<UUID, TasteSearchResult> tastesByRecipe = tasteResults.stream().collect(
                Collectors.toMap(result -> result.getRecipe().getId(), Function.identity(),
                        (first, ignored) -> first));

        Comparator<CombinedSearchResult> order = Comparator
                .comparingInt((CombinedSearchResult result) ->
                        result.ingredientResult().getMatchedCount())
                .reversed();
        if (tasteMode == TasteFilterMode.RANKING) {
            order = order.thenComparing(Comparator
                    .comparingInt((CombinedSearchResult result) ->
                            result.tasteResult().getMatchedCount())
                    .reversed());
        }
        order = order.thenComparing(CombinedSearchResult::getRecipe, RECIPE_ORDER);

        return ingredientResults.stream()
                .filter(result -> tastesByRecipe.containsKey(result.getRecipe().getId()))
                .map(result -> CombinedSearchResult.combined(
                        result, tastesByRecipe.get(result.getRecipe().getId())))
                .sorted(order)
                .toList();
    }
}
