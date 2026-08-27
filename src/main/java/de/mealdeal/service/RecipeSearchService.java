package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Searches an already supplied recipe collection by stable domain identities.
 *
 * <p>The service deliberately knows no repositories or SQL. A higher layer can
 * load recipes and then apply this pure business logic. Ingredient and taste
 * names are not compared, normalized, or interpreted as synonyms.</p>
 */
public final class RecipeSearchService {

    private static final int MAX_SELECTED_INGREDIENTS = 10;
    private static final MathContext RATIO_CONTEXT = MathContext.DECIMAL128;

    private static final Comparator<Recipe> RECIPE_NAME_ORDER =
            Comparator.comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Recipe::getName)
                    .thenComparing(Recipe::getId);

    /** Searches and ranks recipes by selected ingredient identities. */
    public List<IngredientSearchResult> searchByIngredients(
            Collection<Recipe> recipes, Collection<Ingredient> selectedIngredients) {
        List<Recipe> checkedRecipes = validateRecipes(recipes);
        List<Ingredient> selected = validateCriteria(
                selectedIngredients, "Selected ingredients", MAX_SELECTED_INGREDIENTS,
                Ingredient::getId);

        return checkedRecipes.stream()
                .map(recipe -> createIngredientResult(recipe, selected))
                .filter(Objects::nonNull)
                .sorted(resultOrder(IngredientSearchResult::getMatchedCount,
                        IngredientSearchResult::getMatchRatio,
                        IngredientSearchResult::getRecipe))
                .toList();
    }

    /** Filters or ranks recipes by selected taste identities. */
    public List<TasteSearchResult> searchByTastes(
            Collection<Recipe> recipes, Collection<Taste> selectedTastes,
            TasteFilterMode mode) {
        List<Recipe> checkedRecipes = validateRecipes(recipes);
        List<Taste> selected = validateCriteria(
                selectedTastes, "Selected tastes", null, Taste::getId);
        Objects.requireNonNull(mode, "Taste filter mode must not be null.");

        Comparator<TasteSearchResult> order = mode == TasteFilterMode.RANKING
                ? resultOrder(TasteSearchResult::getMatchedCount,
                        TasteSearchResult::getMatchRatio, TasteSearchResult::getRecipe)
                : Comparator.comparing(TasteSearchResult::getRecipe, RECIPE_NAME_ORDER);

        return checkedRecipes.stream()
                .map(recipe -> createTasteResult(recipe, selected))
                .filter(Objects::nonNull)
                .filter(result -> accepts(result, mode))
                .sorted(order)
                .toList();
    }

    private static IngredientSearchResult createIngredientResult(
            Recipe recipe, List<Ingredient> selected) {
        Set<UUID> recipeIngredientIds = recipe.getIngredients().stream()
                .map(RecipeIngredient::getIngredient)
                .map(Ingredient::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Ingredient> matched = selected.stream()
                .filter(ingredient -> recipeIngredientIds.contains(ingredient.getId())).toList();
        if (matched.isEmpty()) {
            return null;
        }
        List<Ingredient> missing = selected.stream()
                .filter(ingredient -> !recipeIngredientIds.contains(ingredient.getId())).toList();
        return new IngredientSearchResult(recipe, matched, missing,
                calculateRatio(matched.size(), selected.size()));
    }

    private static TasteSearchResult createTasteResult(Recipe recipe, List<Taste> selected) {
        Set<UUID> recipeTasteIds = recipe.getTastes().stream()
                .map(Taste::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Taste> matched = selected.stream()
                .filter(taste -> recipeTasteIds.contains(taste.getId())).toList();
        if (matched.isEmpty()) {
            return null;
        }
        List<Taste> missing = selected.stream()
                .filter(taste -> !recipeTasteIds.contains(taste.getId())).toList();
        return new TasteSearchResult(recipe, matched, missing,
                calculateRatio(matched.size(), selected.size()));
    }

    private static boolean accepts(TasteSearchResult result, TasteFilterMode mode) {
        return switch (mode) {
            case AND -> result.getMatchedCount() == result.getSelectedCount();
            case OR, RANKING -> true;
        };
    }

    private static BigDecimal calculateRatio(int matchedCount, int selectedCount) {
        return BigDecimal.valueOf(matchedCount)
                .divide(BigDecimal.valueOf(selectedCount), RATIO_CONTEXT);
    }

    private static List<Recipe> validateRecipes(Collection<Recipe> recipes) {
        Objects.requireNonNull(recipes, "Recipes must not be null.");
        if (recipes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipes must not contain null values.");
        }
        return List.copyOf(recipes);
    }

    private static <T> List<T> validateCriteria(
            Collection<T> criteria, String label, Integer maximum,
            Function<T, UUID> idExtractor) {
        Objects.requireNonNull(criteria, label + " must not be null.");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException(label + " must contain at least one value.");
        }
        if (maximum != null && criteria.size() > maximum) {
            throw new IllegalArgumentException(label + " must not contain more than "
                    + maximum + " values.");
        }
        if (criteria.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }

        Set<UUID> ids = new HashSet<>();
        for (T criterion : criteria) {
            if (!ids.add(idExtractor.apply(criterion))) {
                throw new IllegalArgumentException(label + " must not contain duplicate identities.");
            }
        }
        return List.copyOf(criteria);
    }

    private static <T> Comparator<T> resultOrder(
            Function<T, Integer> matchedCount,
            Function<T, BigDecimal> matchRatio,
            Function<T, Recipe> recipe) {
        return Comparator.<T, Integer>comparing(matchedCount).reversed()
                .thenComparing(matchRatio, Comparator.reverseOrder())
                .thenComparing(recipe, RECIPE_NAME_ORDER);
    }
}
