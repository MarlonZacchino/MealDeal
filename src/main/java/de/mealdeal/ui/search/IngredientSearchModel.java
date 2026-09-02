package de.mealdeal.ui.search;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.ui.IngredientCategoryGrouping;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.IngredientSearchResult;
import de.mealdeal.service.RecipeSearchService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Holds ingredient-selection state and delegates recipe matching to RecipeSearchService. */
public final class IngredientSearchModel {

    public static final int MAX_SELECTED_INGREDIENTS = 10;

    private static final Comparator<Ingredient> INGREDIENT_ORDER = Comparator
            .comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Ingredient::getName)
            .thenComparing(Ingredient::getId);

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeSearchService recipeSearchService;
    private final List<Ingredient> selectedIngredients = new ArrayList<>();

    /** Creates the search state with repositories and the existing search service. */
    public IngredientSearchModel(IngredientRepository ingredientRepository,
                                 RecipeRepository recipeRepository,
                                 RecipeSearchService recipeSearchService) {
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.recipeSearchService = Objects.requireNonNull(
                recipeSearchService, "Recipe search service must not be null.");
    }

    /** Loads all selectable central ingredients in deterministic display order. */
    public List<Ingredient> loadAvailableIngredients() {
        return ingredientRepository.findAll().stream().sorted(INGREDIENT_ORDER).toList();
    }

    /** Groups unselected, filtered ingredients by their persisted category for presentation. */
    public List<IngredientCategoryGrouping.Group> groupAvailableIngredients(
            List<Ingredient> availableIngredients, String filterText) {
        return IngredientCategoryGrouping.group(availableIngredients, filterText,
                selectedIngredients.stream().map(Ingredient::getId).toList());
    }

    /** Adds one unique ingredient unless the ten-item UI limit has been reached. */
    public SelectionResult select(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "Ingredient must not be null.");
        if (selectedIngredients.contains(ingredient)) {
            return SelectionResult.ALREADY_SELECTED;
        }
        if (selectedIngredients.size() >= MAX_SELECTED_INGREDIENTS) {
            return SelectionResult.LIMIT_REACHED;
        }
        selectedIngredients.add(ingredient);
        return SelectionResult.ADDED;
    }

    /** Removes one selected ingredient by its stable identity. */
    public void remove(Ingredient ingredient) {
        selectedIngredients.remove(Objects.requireNonNull(
                ingredient, "Ingredient must not be null."));
    }

    public List<Ingredient> getSelectedIngredients() {
        return List.copyOf(selectedIngredients);
    }

    /** Removes every selected ingredient. */
    public void clear() {
        selectedIngredients.clear();
    }

    /** Loads recipes and delegates matching and ranking unchanged to RecipeSearchService. */
    public List<IngredientSearchResult> search() {
        if (selectedIngredients.isEmpty()) {
            throw new IllegalArgumentException("Select at least one ingredient.");
        }
        return recipeSearchService.searchByIngredients(
                recipeRepository.findAll(), selectedIngredients);
    }

    public enum SelectionResult {
        ADDED,
        ALREADY_SELECTED,
        LIMIT_REACHED
    }

}
